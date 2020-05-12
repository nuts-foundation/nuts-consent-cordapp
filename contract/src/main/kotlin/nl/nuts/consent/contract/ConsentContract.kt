/*
 *     Nuts consent cordapp
 *     Copyright (C) 2019 Nuts community
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package nl.nuts.consent.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.transactions.LedgerTransaction
import nl.nuts.consent.model.ConsentMetadata
import nl.nuts.consent.state.BranchState
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.JarEntry

/**
 * The consent contract. It validates state transitions based on various commands.
 */
class ConsentContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val CONTRACT_ID = "nl.nuts.consent.contract.ConsentContract"

        fun extractMetadata(attachment: Attachment) : ConsentMetadata {
            val inStream = attachment.openAsJAR()
            var entry: JarEntry = inStream.nextJarEntry

            try {
                while (!entry.name.endsWith("json")) {
                    entry = inStream.nextJarEntry
                }
            }
            catch(e :IllegalStateException) {
                throw IllegalStateException("Attachment is missing required metadata file")
            }

            val reader = inStream.bufferedReader(Charset.forName("UTF8"))

            return Serialisation.objectMapper().readValue(reader, ConsentMetadata::class.java)
        }
    }

    object Serialisation {
        val _objectMapper : ObjectMapper by lazy {
            val objectMapper = ObjectMapper()
            objectMapper.findAndRegisterModules()
            objectMapper.dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            objectMapper
        }

        fun objectMapper() : ObjectMapper{
            return _objectMapper
        }
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<ConsentCommands>()
        var totalAttachments = 0
        // check uniqueness of commands
        commands.forEach {
            requireThat {
                "Commands with requireSingleCommand=true must run in a standalone transaction" using (!it.value.requireSingleCommand() || (it.value.requireSingleCommand() && commands.size == 1))
            }
            it.value.verifyStates(tx)

            totalAttachments += it.value.requiredNumberOfAttachments(tx)
        }

        // check for correct total amount of attachments
        requireThat {
            "The correct number of attachments are included when multiple commands are used" using (tx.attachments.filter { it !is ContractAttachment }.size == totalAttachments)
        }
    }

    // Used to indicate the transaction's intent.
    interface ConsentCommands : CommandData {

        /**
         * How many attachments are needed per command
         */
        fun requiredNumberOfAttachments(tx: LedgerTransaction) : Int

        /**
         * Can a command be executed together with another command
         */
        fun requireSingleCommand() : Boolean

        /**
         * Each command has its own set of rules
         */
        fun verifyStates(tx: LedgerTransaction)

        /**
         * Command used in CreateGenesisConsentState flow for creating the first record.
         */
        class GenesisCommand : ConsentCommands {
            override fun requiredNumberOfAttachments(tx: LedgerTransaction)  = 0
            override fun requireSingleCommand() = true

            override fun verifyStates(tx: LedgerTransaction) {
                val command = tx.commands.single { it.value == this }
                val attachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.isEmpty())
                    "The right amount of states are created" using (tx.outputs.size == 1)

                    val out = tx.outputsOfType<ConsentState>().single()

                    "Version number is 1" using (out.version == 1)
                    "There is one participant" using (out.participants.size == 1)
                    "All participants must be signers" using (command.signers.containsAll(out.participants.map { it.owningKey }))
                    "The output state does not have any attachments" using (out.attachments.isEmpty())
                    "The transaction does not have any additional attachments" using (attachments.isEmpty())
                }
            }
        }

        /**
         * constraints that holds for both Add and Update commands
         */
        abstract class BranchCommand : ConsentCommands {
            override fun verifyStates(tx: LedgerTransaction) {
                val command = tx.commands.single { it.value == this }
                val attachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.size == 1)
                    "The right amount of states are created" using (tx.outputs.size == 2)

                    "Only ConsentStates are consumed" using tx.inputs.all { it.state.data is ConsentState }
                    "1 ConsentState is produced" using (tx.outputsOfType<ConsentState>().size == 1)
                    "1 ConsentBranch is produced" using (tx.outputsOfType<ConsentBranch>().size == 1)
                }

                val consentIn = tx.inputsOfType<ConsentState>().single()
                val consentOut = tx.outputsOfType<ConsentState>().single()
                val branchOut = tx.outputsOfType<ConsentBranch>().single()

                requireThat {
                    "Output state has the same UUID as input state" using (consentOut.linearId == consentIn.linearId)
                    "Output branch does have an open state" using (branchOut.state == BranchState.Open)
                    "Version number is 1 more" using (consentOut.version - consentIn.version == 1)

                    "ConsentBranch has the same externalId as ConsentState" using (consentOut.linearId.externalId == branchOut.linearId.externalId)

                    "All new participants are unique" using (branchOut.participants.toSet().size == branchOut.participants.size)
                    "All new participants must be signers" using (command.signers.containsAll(branchOut.participants.map { it.owningKey }))

                    "ConsentState participants remains the same" using (consentOut.participants.containsAll(consentIn.participants))
                    "ConsentState participants remains the same" using (consentIn.participants.containsAll(consentOut.participants))

                    "There must at least be 1 attachment" using (attachments.isNotEmpty())
                    "ConsentBranch must add new attachments" using ((consentIn.attachments + branchOut.attachments).size > consentIn.attachments.size)

                    "Attachments have not changed" using (consentIn.attachments.containsAll(consentOut.attachments))
                    "Attachments have not changed" using (consentOut.attachments.containsAll(consentIn.attachments))
                }

                val metadataList = attachments.map {extractMetadata(it)}
                // raises IllegalState when invalid with correct message
                metadataList.forEach { it.verify() }

                val legalEntsPerAtt = metadataList.map { itOuter -> itOuter.organisationSecureKeys.map { it.legalEntity } }

                // legalEntities in metadata must match the list in the output state
                legalEntsPerAtt.forEach {
                    requireThat {
                        "legal entities in attachments do not match those in ConsentBranch" using (branchOut.legalEntities.toTypedArray().contentDeepEquals(it.toTypedArray()))
                    }
                }

                requireThat {
                    "Attachments in state have the same amount as include in the transaction" using (branchOut.attachments.size == attachments.size)
                    "All attachments in state are include in the transaction" using (attachments.map { it.id }.containsAll(branchOut.attachments))

                    "No signatures are present" using branchOut.signatures.isEmpty()
                }
            }
        }

        /**
         * Command for adding new consent to an existing ConsentState, may be combined with an UpdateCommand
         */
        class AddCommand : BranchCommand() {
            override fun requiredNumberOfAttachments(tx: LedgerTransaction) = 1
            override fun requireSingleCommand() = false

            override fun verifyStates(tx: LedgerTransaction) {
                super.verifyStates(tx)
                val attachments = tx.attachments.filter { it !is ContractAttachment }
                val metadataList = attachments.map {extractMetadata(it)}

                requireThat {
                    "a new attachment must be added" using (metadataList.find { it.previousAttachmentId == null } != null)
                }
            }
        }

        /**
         * Command for updating existing consent, may be combined with an AddCommand
         */
        class UpdateCommand : BranchCommand() {
            // only new one is relevant, old one is obsolete
            override fun requiredNumberOfAttachments(tx: LedgerTransaction) = 1
            override fun requireSingleCommand() = false

            override fun verifyStates(tx: LedgerTransaction) {
                super.verifyStates(tx)
                val attachments = tx.attachments.filter { it !is ContractAttachment }
                val consentIn = tx.inputsOfType<ConsentState>().single()
                val branchOut = tx.outputsOfType<ConsentBranch>().single()
                val metadataList = attachments.map {extractMetadata(it)}

                var updateFound = false

                // attachments may have previous reference, if so the old attachment must exist
                requireThat {
                    metadataList.forEach {
                        if (it.previousAttachmentId != null) {
                            updateFound = true
                            "attachment referenced by new attachment must not be attached" using (!branchOut.attachments.contains(SecureHash.parse(it.previousAttachmentId)))
                            "attachment referenced by new attachment must be present in input state" using (consentIn.attachments.contains(SecureHash.parse(it.previousAttachmentId)))
                        }
                    }
                    "at least 1 update exists in attachment list" using (updateFound)
                }
            }
        }

        /**
         * Command for adding a signature to a ConsentBranch
         */
        class SignCommand : ConsentCommands {
            override fun requiredNumberOfAttachments(tx: LedgerTransaction) : Int {
                val branchIn = tx.inputsOfType<ConsentBranch>().single()
                return branchIn.attachments.size
            }

            override fun requireSingleCommand() = true

            override fun verifyStates(tx: LedgerTransaction) {
                val command = tx.commands.single { it.value == this }
                val attachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "1 ConsentBranch is consumed" using (tx.inputsOfType<ConsentBranch>().size == 1)
                    "1 ConsentBranch is produced" using (tx.outputsOfType<ConsentBranch>().size == 1)
                }

                val branchIn = tx.inputsOfType<ConsentBranch>().single()
                val branchOut = tx.outputsOfType<ConsentBranch>().single()

                requireThat {
                    "Output state has the same UUID as input state" using (branchIn.linearId == branchOut.linearId)
                    "Input state does have an open state" using (branchIn.state == BranchState.Open)

                    "All new participants are unique" using (branchOut.participants.toSet().size == branchOut.participants.size)
                    "All new participants must be signers" using (command.signers.containsAll(branchOut.participants.map { it.owningKey }))

                    "Participants remains the same" using (branchOut.participants.containsAll(branchIn.participants))
                    "Participants remains the same" using (branchIn.participants.containsAll(branchOut.participants))

                    "LegalEntities remain the same" using (branchOut.legalEntities.containsAll(branchIn.legalEntities))
                    "LegalEntities remain the same" using (branchIn.legalEntities.containsAll(branchOut.legalEntities))

                    "initiatingNode remains the same" using (branchOut.initiatingNode == branchIn.initiatingNode)
                    "initiatingLegalEntity remains the same" using (branchOut.initiatingLegalEntity == branchIn.initiatingLegalEntity)

                    "Attachments in state have the same amount as include in the transaction" using (branchOut.attachments.size == attachments.size)
                    "All attachments in state are include in the transaction" using (branchOut.attachments.subtract(attachments.map { it.id }.toSet()).isEmpty())
                    "Attachments have not changed" using (branchIn.attachments.containsAll(branchOut.attachments))
                    "Attachments have not changed" using (branchOut.attachments.containsAll(branchIn.attachments))

                    "1 more signature is present" using (branchOut.signatures.size == (branchIn.signatures.size + 1))
                    "All attachment signatures are unique" using (branchOut.signatures.size == branchOut.signatures.toSet().size)
                    "All signatures belong to attachments" using (branchOut.attachments.containsAll(branchOut.signatures.map { it.attachmentHash }))
                    "All signatures are valid" using (branchOut.signatures.all { it.verify() })
                }

                val metadataList = attachments.map {extractMetadata(it)}
                // raises IllegalState when invalid with correct message
                metadataList.forEach { it.verify() }
                val legalEntsPerAtt = metadataList.map { itOuter -> itOuter.organisationSecureKeys.map { it.legalEntity } }

                // legalEntity in AttachmentSignature must match those in metadata
                if (!branchOut.signatures.map { it.legalEntityURI }.all { legalEntsPerAtt.flatten().contains(it) }) {
                    throw IllegalArgumentException("unknown legalEntity found in attachmentSignatures, not present in attachments")
                }

                // legalEntities in metadata must match the list in the output state
                legalEntsPerAtt.forEach {
                    requireThat {
                        "legal entities in attachments do not match those in consentRequestState" using (branchOut.legalEntities.toTypedArray().contentDeepEquals(it.toTypedArray()))
                    }
                }
            }
        }

        /**
         * Command for adding a signature to a ConsentBranch
         */
        class CloseCommand : ConsentCommands {
            override fun requiredNumberOfAttachments(tx: LedgerTransaction) : Int {
                val branchIn = tx.inputsOfType<ConsentBranch>().single()
                return branchIn.attachments.size
            }

            override fun requireSingleCommand() = true

            override fun verifyStates(tx: LedgerTransaction) {
                val command = tx.commands.single { it.value == this }
                val attachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "1 ConsentBranch is consumed" using (tx.inputsOfType<ConsentBranch>().size == 1)
                    "1 ConsentBranch is produced" using (tx.outputsOfType<ConsentBranch>().size == 1)
                }

                val branchIn = tx.inputsOfType<ConsentBranch>().single()
                val branchOut = tx.outputsOfType<ConsentBranch>().single()

                requireThat {
                    "Output state has the same UUID as input state" using (branchIn.linearId == branchOut.linearId)
                    "Output state does not have an open state" using (branchOut.state != BranchState.Open)

                    "All new participants are unique" using (branchOut.participants.toSet().size == branchOut.participants.size)
                    "All new participants must be signers" using (command.signers.containsAll(branchOut.participants.map { it.owningKey }))

                    "Participants remains the same" using (branchOut.participants.containsAll(branchIn.participants))
                    "Participants remains the same" using (branchIn.participants.containsAll(branchOut.participants))

                    "LegalEntities remain the same" using (branchOut.legalEntities.containsAll(branchIn.legalEntities))
                    "LegalEntities remain the same" using (branchIn.legalEntities.containsAll(branchOut.legalEntities))

                    "Attachments in state have the same amount as include in the transaction" using (branchOut.attachments.size == attachments.size)
                    "All attachments in state are include in the transaction" using (branchOut.attachments.subtract(attachments.map { it.id }.toSet()).isEmpty())
                    "Attachments have not changed" using (branchIn.attachments.containsAll(branchOut.attachments))
                    "Attachments have not changed" using (branchOut.attachments.containsAll(branchIn.attachments))
                }
            }
        }

        /**
         * Command for merging a ConsentBranch and ConsentState into ConsentState
         */
        class MergeCommand : ConsentCommands {
            override fun requiredNumberOfAttachments(tx: LedgerTransaction) : Int {
                val branchIn = tx.inputsOfType<ConsentBranch>().single()
                val consentIn = tx.inputsOfType<ConsentState>().single()

                val attachments = tx.attachments.filter { it !is ContractAttachment }
                val referencedAttachments = branchIn.referencedAttachments(attachments.map { it.id to it}.toMap())
                val unreferencedAttachments = consentIn.attachments.filter { !referencedAttachments.contains(it) }

                return (branchIn.attachments + unreferencedAttachments).size
            }

            override fun requireSingleCommand() = true

            override fun verifyStates(tx: LedgerTransaction) {
                val command = tx.commands.single { it.value == this }
                val attachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "1 ConsentBranch is consumed" using (tx.inputsOfType<ConsentBranch>().size == 1)
                    "1 ConsentState is consumed" using (tx.inputsOfType<ConsentState>().size == 1)
                    "1 ConsentState is produced" using (tx.outputsOfType<ConsentState>().size == 1)
                }

                val branchIn = tx.inputsOfType<ConsentBranch>().single()
                val consentIn = tx.inputsOfType<ConsentState>().single()
                val consentOut = tx.outputsOfType<ConsentState>().single()

                val referencedAttachments = branchIn.referencedAttachments(attachments.map { it.id to it}.toMap())
                val unreferencedAttachments = consentIn.attachments.filter { !referencedAttachments.contains(it) }

                requireThat {
                    "Output state has the same UUID as input state" using (consentIn.linearId == consentOut.linearId)
                    "Input state does have an open state" using (branchIn.state == BranchState.Open)
                    "Version number is 1 more" using (consentOut.version - consentIn.version == 1)

                    "All participants are unique" using (consentOut.participants.toSet().size == consentOut.participants.size)
                    "All participants must be signers" using (command.signers.containsAll(consentOut.participants.map { it.owningKey }))

                    "Participants are a union of input states" using (consentOut.participants.containsAll(consentIn.participants))
                    "Participants are a union of input states" using (consentOut.participants.containsAll(branchIn.participants))

                    "Attachments in state have the same amount as include in the transaction" using (consentOut.attachments.size == attachments.size)
                    "All attachments in state are include in the transaction" using (attachments.map { it.id }.containsAll(consentOut.attachments))
                    "Attachments include unreferenced attachments" using (consentOut.attachments.containsAll(unreferencedAttachments))
                    "Attachments include new attachments" using (consentOut.attachments.containsAll(branchIn.attachments))
                    "All referenced attachments are available in input state" using (consentIn.attachments.containsAll(referencedAttachments))

                    "All signature are present" using (branchIn.signatures.size == (branchIn.attachments.size * branchIn.legalEntities.size))
                    "All attachment signatures are unique" using (branchIn.signatures.size == branchIn.signatures.toSet().size)
                    "All signatures belong to attachments" using (branchIn.attachments.containsAll(branchIn.signatures.map { it.attachmentHash }))
                    "All signatures are valid" using (branchIn.signatures.all { it.verify() })
                }
            }
        }
    }
}
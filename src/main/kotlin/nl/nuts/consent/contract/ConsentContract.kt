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

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import nl.nuts.consent.state.ConsentRequestState
import nl.nuts.consent.state.ConsentState

/**
 * The consent contract. It validates state transitions based on various commands.
 */
class ConsentContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val CONTRACT_ID = "nl.nuts.consent.contract.ConsentContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<ConsentCommands>()

        // verification delegated to specific commands
        command.value.verifyStates(tx)
    }

    // Used to indicate the transaction's intent.
    interface ConsentCommands : CommandData {

        fun verifyStates(tx: LedgerTransaction)

        /**
         * Generic checks valid for all different commands
         */
        open class GenericRequest : ConsentCommands {
            override fun verifyStates(tx: LedgerTransaction) {
                verifyInputState(tx)
                verifyOutputState(tx)
                verifyAttachments(tx)
            }

            open fun verifyInputState(tx: LedgerTransaction) {

            }

            open fun verifyOutputState(tx: LedgerTransaction) {
                val command = tx.commands.requireSingleCommand<ConsentCommands>()

                requireThat {
                    "The right amount of states are created" using (tx.outputs.size == 1)

                    val out = tx.outputsOfType<LinearState>().single()

                    "All participants are unique" using (out.participants.toSet().size == out.participants.size)
                    "All participants must be signers" using (command.signers.containsAll(out.participants.map { it.owningKey }))
                }
            }

            open fun verifyAttachments(tx: LedgerTransaction) {
                val attachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "There must at least be 1 attachment" using (attachments.isNotEmpty())
                }
            }
        }

        /**
         * Abstract class with ConsentRequestState as input
         */
        open class ProcessRequest : GenericRequest() {
            override fun verifyInputState(tx: LedgerTransaction) {
                "The right amount of states are consumed" using (tx.inputs.size == 1)
                "Only ConsentRequestStates are consumed" using (tx.inputs.all { it.state.data is ConsentRequestState })
            }

            fun verifyAttachmentsWithState(tx: LedgerTransaction, state: ConsentRequestState) {
                val txAttachments = tx.attachments.filter { it !is ContractAttachment }

                "Attachments in state have the same amount as include in the transaction" using (state.attachments.size == txAttachments.size)
                "All attachments in state are include in the transaction" using (arrayOf(state.attachments.toList()) contentEquals arrayOf(txAttachments.map{it.id}))

                "All attachment signatures are unique" using (state.signatures.size == state.signatures.toSet().size)
                "All signatures belong to signing parties" using (state.participants.containsAll(state.signatures.map{it.party}))
                "All signatures belong to attachments" using (state.attachments.containsAll(state.signatures.map{it.attachmentHash}))
                "All signatures are valid" using (state.signatures.all{it.verify()})
            }
        }

        /**
         * Initiate a new request for a new consentState
         */
        class CreateRequest : GenericRequest() {
            override fun verifyStates(tx: LedgerTransaction) {
                super.verifyStates(tx)
                val txAttachments = tx.attachments.filter { it !is ContractAttachment }

                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.isEmpty())
                    "Only ConsentRequestStates are created" using (tx.outputs.all { it.data is ConsentRequestState })

                    val out = tx.outputsOfType<ConsentRequestState>().first()

                    "Attachments in state have the same amount as included in the transaction" using (out.attachments.size == txAttachments.size)
                    "All attachments in state are include in the transaction" using (arrayOf(out.attachments.toList()) contentEquals arrayOf(txAttachments.map{it.id}))
                }
            }
        }

        /**
         * Command for a Party to signal that it verified the encrypted contents
         */
        class AcceptRequest : ProcessRequest() {
            override fun verifyOutputState(tx: LedgerTransaction) {
                super.verifyOutputState(tx)

                requireThat {
                    "Only ConsentRequestStates are created" using (tx.outputs.all { it.data is ConsentRequestState })
                }
            }

            override fun verifyAttachments(tx: LedgerTransaction) {
                super.verifyAttachments(tx)

                val inState = tx.inputsOfType<ConsentRequestState>().first()
                val out = tx.outputsOfType<ConsentRequestState>().first()

                verifyAttachmentsWithState(tx, out)

                requireThat {
                    "Output state has [attachments] more signatures than input state" using (out.signatures.size - inState.signatures.size == out.attachments.size)
                }
            }
        }

        /**
         * Command to finalize the request. All parties have done additional checks on the encrypted attachment data
         */
        class FinalizeRequest : ProcessRequest() {
            override fun verifyOutputState(tx: LedgerTransaction) {
                super.verifyOutputState(tx)

                requireThat {
                    "Only ConsentStates are created" using (tx.outputs.all { it.data is ConsentState })
                }
            }

            override fun verifyAttachments(tx: LedgerTransaction) {
                super.verifyAttachments(tx)

                val inState = tx.inputsOfType<ConsentRequestState>().first()

                verifyAttachmentsWithState(tx, inState)

                requireThat {
                    // check if the right amount of attachments are present, given that no duplicate may exist, this is enough
                    "All signatures are present" using (inState.signatures.size == inState.participants.size * inState.attachments.size)
                }
            }
        }
    }
}
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
                val command = tx.commands.requireSingleCommand<ConsentCommands>()

                requireThat {
                    "The right amount of states are created" using (tx.outputs.size == 1)
                    val out = tx.outputsOfType<LinearState>().single()
                    "All participants are unique" using (out.participants.toSet().size == out.participants.size)
                    "All participants must be signers" using (command.signers.containsAll(out.participants.map { it.owningKey }))

                    "The number of attachments must be at least 1" using (tx.attachments.size >= 1)

                    "All attachments must be signed by all participants" using (tx.attachments.all { it.signerKeys.containsAll(command.signers) })
                }
            }
        }

        /**
         * Initiate a new request for a new consentState
         */
        class CreateRequest : GenericRequest() {
            override fun verifyStates(tx: LedgerTransaction) {
                super.verifyStates(tx)
                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.size == 0)
                    "Only ConsentRequestStates are created" using (tx.outputs.all { it.data is ConsentRequestState })

                    val out = tx.outputsOfType<ConsentRequestState>().first()

                    "Attachments in state have the same amount as include in the transaction" using (out.attachments.size == tx.attachments.size)
                    "All attachments in state are include in the transaction" using (arrayOf(out.attachments) contentEquals arrayOf(tx.attachments.map{it.id}))
                }
            }
        }

        /**
         * Command for a Party to signal that it verified the encrypted contentss
         */
        class AcceptRequest : GenericRequest() {
            override fun verifyStates(tx: LedgerTransaction) {
                super.verifyStates(tx)
                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.size == 1)
                    "Only ConsentRequestStates are consumed" using (tx.inputs.all { it.state.data is ConsentRequestState })
                    "The right amount of states are created" using (tx.outputs.size == 1)
                    "Only ConsentRequestStates are created" using (tx.outputs.all { it.data is ConsentRequestState })

                    val inState = tx.inputsOfType<ConsentRequestState>().first()
                    val out = tx.outputsOfType<ConsentRequestState>().first()

                    "All attachments are unique" using (out.attachments.size == out.attachments.toSet().size)
                    "Attachments in state have the same amount as include in the transaction" using (out.attachments.size == tx.attachments.size)
                    "All attachments in state are include in the transaction" using (arrayOf(out.attachments) contentEquals arrayOf(tx.attachments.map{it.id}))

                    "All attachment signatures are unique" using (out.signatures.size == out.signatures.toSet().size)
                    "All signatures belong to signing parties" using (out.participants.containsAll(out.signatures.map{it.party}))
                    "All signatures belong to attachments" using (out.attachments.containsAll(out.signatures.map{it.attachmentHash}))
                    "All signatures are valid" using (out.signatures.all{it.verify()})

                    "Output state has [attachments] more signatures than input state" using (out.signatures.size - inState.signatures.size == out.attachments.size)
                }
            }
        }

        /**
         * Command to finalize the request. All parties have done additional checks on the encrypted attachment data
         */
        class FinalizeRequest : GenericRequest() {
            override fun verifyStates(tx: LedgerTransaction) {
                super.verifyStates(tx)
                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.size == 1)
                    "Only ConsentRequestStates are consumed" using (tx.inputs.all { it.state.data is ConsentRequestState })
                    "Only ConsentStates are created" using (tx.outputs.all { it.data is ConsentState })

                    val inState = tx.inputsOfType<ConsentRequestState>().first()

                    "All attachments are unique" using (inState.attachments.size == inState.attachments.toSet().size)

                    "Attachments in state have the same amount as include in the transaction" using (inState.attachments.size == tx.attachments.size)
                    "All attachments in state are include in the transaction" using (arrayOf(inState.attachments) contentEquals arrayOf(tx.attachments.map{it.id}))

                    "All attachment signatures are unique" using (inState.signatures.size == inState.signatures.toSet().size)

                    "The set of attachment signature parties and signing parties are the same" using (inState.participants.toSet() == inState.signatures.map{it.party}.toSet())
                    "The set of attachment signature hashes and attachment hashes are the same" using (inState.attachments.toSet() == inState.signatures.map{it.attachmentHash}.toSet())
                    "All signatures are valid" using (inState.signatures.all{it.verify()})

                    // parties * attachments
                    "All signatures are present" using (inState.signatures.size == inState.participants.size * inState.attachments.size)
                }
            }
        }
    }
}
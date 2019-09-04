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
package nl.nuts.test

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import nl.nuts.test.ActiveState
import nl.nuts.test.AddState
import nl.nuts.test.GenesisState

class TestContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val CONTRACT_ID = "nl.nuts.test.TestContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<TestCommands>()

        // verification delegated to specific commands
        command.value.verifyStates(tx)
    }

    // Used to indicate the transaction's intent.
    interface TestCommands : CommandData {

        fun verifyStates(tx:LedgerTransaction)

        class GenesisCommand : TestCommands {
            override fun verifyStates(tx: LedgerTransaction) {

                val command = tx.commands.requireSingleCommand<GenesisCommand>()

                requireThat {
                    "The right amount of states are created" using (tx.outputs.size == 1)

                    val out = tx.outputsOfType<GenesisState>().single()

                    "There is one participant" using (out.participants.size == 1)
                    "All participants must be signers" using (command.signers.containsAll(out.participants.map { it.owningKey }))
                }
            }
        }

        class BranchCommand : TestCommands {
            override fun verifyStates(tx: LedgerTransaction) {

                val command = tx.commands.requireSingleCommand<BranchCommand>()

                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.size == 1)
                    "The right amount of states are created" using (tx.outputs.size == 2)

                    val genesisIn = tx.inputsOfType<GenesisState>().single()
                    val genesisOut = tx.outputsOfType<GenesisState>().single()
                    val addOut = tx.outputsOfType<AddState>().single()

                    "There is more than one participant" using (addOut.participants.size > 1)
                    "All participants must be signers" using (command.signers.containsAll(addOut.participants.map { it.owningKey }))
                    "genesis is propagated correctly" using (genesisIn.uuid == genesisOut.uuid)
                    "AddState is using correct externalId" using (genesisIn.uuid.externalId == addOut.uuid.externalId)
                }
            }
        }

        class MergeCommand : TestCommands {
            override fun verifyStates(tx: LedgerTransaction) {

                val command = tx.commands.requireSingleCommand<MergeCommand>()

                requireThat {
                    "The right amount of states are consumed" using (tx.inputs.size == 2)
                    "The right amount of states are created" using (tx.outputs.size == 1)

                    val genesisIn = tx.inputsOfType<GenesisState>().single()
                    val genesisRefIn = tx.inRefsOfType<GenesisState>().single()
                    val activeOut = tx.outputsOfType<ActiveState>().single()
                    val addIn = tx.inputsOfType<AddState>().single()

                    "There is more than one participant" using (addIn.participants.size > 1)
                    "All participants must be signers" using (command.signers.containsAll(addIn.participants.map { it.owningKey }))
                    "Active contains all participants in AddIn" using (activeOut.participants.containsAll(addIn.participants) )
                    "Active contains all participants in GenesisIn" using (activeOut.participants.containsAll(genesisIn.participants) )
                    "genesis is propagated correctly" using (genesisIn.uuid == activeOut.uuid)
                    "AddState is using correct externalId" using (genesisIn.uuid.externalId == addIn.uuid.externalId)
                }
            }
        }
    }
}
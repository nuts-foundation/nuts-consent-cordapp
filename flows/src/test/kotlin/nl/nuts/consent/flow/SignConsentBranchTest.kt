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

package nl.nuts.consent.flow

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import kotlin.test.assertEquals

class SignConsentBranchTest  : GenericFlowTests() {
    @Test
    fun `recorded transaction has 1 input, 1 outputs and a single attachment`() {
        val genesisTx = runGenesisTransaction("mergeConsentTest-1")
        val genesisState = genesisTx.tx.outputStates.first() as ConsentState
        val branchTx = runAddTransaction(genesisState.uuid)
        val branchState = branchTx.tx.outputsOfType<ConsentBranch>().first()
        val signedTx = runSignTransaction(branchState.uuid)

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assertEquals(1, txOutputs.size)

            val txInputs = recordedTx.tx.inputs
            assertEquals(1, txInputs.size)

            val attachments = recordedTx.tx.attachments
            assertEquals(2, attachments.size) // the first attachment is the contract and state jar
        }
    }

    private fun runGenesisTransaction(externalId: String): SignedTransaction {
        val flow = ConsentFlows.CreateGenesisConsentState(externalId)
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }

    private fun runAddTransaction(uuid: UniqueIdentifier): SignedTransaction {
        val flow = ConsentFlows.CreateConsentBranch(uuid, setOf(validHash!!), setOf("http://nuts.nl/naming/organisation#test"), setOf(b.info.singleIdentity().name))
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }

    private fun runSignTransaction(uuid: UniqueIdentifier): SignedTransaction {
        val attSig = AttachmentSignature("http://nuts.nl/naming/organisation#test", validHash!!, b.services.keyManagementService.sign(validHash!!.bytes, b.info.legalIdentities.first().owningKey))
        val flow = ConsentFlows.SignConsentBranch(uuid, listOf(attSig))
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }
}
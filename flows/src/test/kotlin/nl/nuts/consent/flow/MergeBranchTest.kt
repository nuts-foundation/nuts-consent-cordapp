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
import net.corda.testing.node.internal.startFlow
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.flow.model.NutsFunctionalContext
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.assertEquals

class MergeBranchTest : GenericFlowTests() {
    @Test
    fun `recorded transaction has 1 input, 1 outputs and a single attachment`() {
        val genesisTx = runGenesisTransaction("mergeConsentTest-1")
        val genesisState = genesisTx.tx.outputStates.first() as ConsentState
        val branchTx = runAddTransaction(genesisState.linearId)
        val branchState = branchTx.tx.outputsOfType<ConsentBranch>().first()
        runSignTransaction(branchState.linearId)
        val signedTx = runMergeTransaction(branchState.linearId)

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assertEquals(1, txOutputs.size)

            val txInputs = recordedTx.tx.inputs
            assertEquals(2, txInputs.size)

            val attachments = recordedTx.tx.attachments
            assertEquals(2, attachments.size) // the first attachment is the contract and state jar
        }
    }

    private fun runGenesisTransaction(externalId: String): SignedTransaction {
        val flow = ConsentFlows.CreateGenesisConsentState(externalId)
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runAddTransaction(uuid: UniqueIdentifier): SignedTransaction {
        val flow = ConsentFlows.CreateConsentBranch(UUID.randomUUID(), uuid, setOf(validHashAdd1!!), setOf(b.info.singleIdentity().name),
            NutsFunctionalContext(setOf("http://nuts.nl/naming/organisation#test")))
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runSignTransaction(uuid: UniqueIdentifier): SignedTransaction {
        val attSig = AttachmentSignature("http://nuts.nl/naming/organisation#test", validHashAdd1!!, b.services.keyManagementService.sign(validHashAdd1!!.bytes, b.info.legalIdentities.first().owningKey))
        val flow = ConsentFlows.SignConsentBranch(uuid, listOf(attSig))
        val future = b.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runMergeTransaction(uuid: UniqueIdentifier) : SignedTransaction {
        val flow = ConsentFlows.MergeBranch(uuid)
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }
}
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

import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.sql.SQLException
import javax.persistence.PersistenceException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateGenesisConsentStateTest : GenericFlowTests() {

    @Test
    fun `recorded transaction has no inputs, a single output and no additional attachments`() {
        val flow = ConsentFlows.CreateGenesisConsentState("externalId")
        val future = a.services.startFlow(flow)
        network.runNetwork()
        val signedTx =  future.resultFuture.getOrThrow()

        // We check the recorded transaction in the vault.
        val recordedTx = a.services.validatedTransactions.getTransaction(signedTx.id)
        val txOutputs = recordedTx!!.tx.outputs
        assertEquals(1, txOutputs.size)

        val txInputs = recordedTx.tx.inputs
        assertEquals(0, txInputs.size)

        val attachments = recordedTx.tx.attachments
        assertEquals(1, attachments.size) // the first attachment is the contract and state jar

        val recordedState = txOutputs[0].data as ConsentState
        assertEquals("externalId", recordedState.linearId.externalId)
    }

    @Test
    fun `2nd transaction for externalId fails`() {
        runTransaction("unique")

        val flow = ConsentFlows.CreateGenesisConsentState("unique")
        a.services.startFlow(flow)
        network.runNetwork()

        assertTrue(a.smm.flowHospital.contains(flowId = flow.runId))
    }

    @Test
    fun `duplicate transaction for externalId results in 1 vault state`() {
        runTransaction("unique-3")

        val flow = ConsentFlows.CreateGenesisConsentState("unique-3")
        a.services.startFlow(flow)
        network.runNetwork()

        // 1 result
        val results = builder {
            val criteria = QueryCriteria.LinearStateQueryCriteria(
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(nl.nuts.consent.state.ConsentState::class.java))


            a.services.vaultService.queryBy<ConsentState>(criteria)
        }

        assertEquals(1, results.states.size)
    }

    private fun runTransaction(externalId: String) : SignedTransaction {
        val flow = ConsentFlows.CreateGenesisConsentState(externalId)
        val future = a.services.startFlow(flow)
        network.runNetwork()

        // the transaction has not been completed but is stuck in an error condition waiting to be re-evaluated
        return future.resultFuture.getOrThrow(1.seconds)
    }
}
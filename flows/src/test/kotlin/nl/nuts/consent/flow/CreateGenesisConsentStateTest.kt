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
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateGenesisConsentStateTest : GenericFlowTests() {

    @Test
    fun `recorded transaction has no inputs, a single output and no additional attachments`() {
        val flow = ConsentFlows.CreateGenesisConsentState("externalId")
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx =  future.getOrThrow()

        // We check the recorded transaction in the vault.
        val recordedTx = a.services.validatedTransactions.getTransaction(signedTx.id)
        val txOutputs = recordedTx!!.tx.outputs
        assertEquals(1, txOutputs.size)

        val txInputs = recordedTx.tx.inputs
        assertEquals(0, txInputs.size)

        val attachments = recordedTx.tx.attachments
        assertEquals(1, attachments.size) // the first attachment is the contract and state jar

        val recordedState = txOutputs[0].data as ConsentState
        assertEquals("externalId", recordedState.uuid.externalId)
    }

    @Test
    fun `2nd transaction for externalId fails`() {
        runTransaction("unique")
        assertFailsWith(SQLException::class) {
            runTransaction("unique")
        }
    }

    @Test
    fun `duplicate transaction for externalId results in 1 vault state`() {
        runTransaction("unique-3")
        assertFailsWith(SQLException::class) {
            runTransaction("unique-3")
        }

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
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }
}
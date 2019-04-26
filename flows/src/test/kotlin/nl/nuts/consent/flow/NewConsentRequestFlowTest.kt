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

import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import nl.nuts.consent.state.ConsentRequestState
import org.junit.Test
import kotlin.test.assertEquals

class NewConsentRequestFlowTest : GenericFlowTests() {

    @Test
    fun `recorded transaction has no inputs, a single output and a single attachment`() {
        val signedTx = runCorrectTransaction()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assertEquals(1, txOutputs.size)

            val txInputs = recordedTx.tx.inputs
            assertEquals(0, txInputs.size)

            val attachments = recordedTx.tx.attachments
            assertEquals(2, attachments.size) // the first attachment is the contract and state jar

            val recordedState = txOutputs[0].data as ConsentRequestState
            assertEquals("uuid", recordedState.consentStateUUID.externalId)
        }
    }

    // todo: enable after persisted state has been added
    // @Test
//    fun `external ID can only be used once`() {
//        val hash = a.services.attachments.importAttachment(validAttachment.inputStream(), a.info.legalIdentities.first().name.toString(), null)
//
//        runCorrectTransaction(hash)
//
//        assertFailsWith(Exception::class) {
//            runCorrectTransaction(hash)
//        }
//
//
//    }

    override fun runCorrectTransaction() : SignedTransaction {
        val flow = ConsentRequestFlows.NewConsentRequest("uuid", setOf(validHash!!), listOf(b.info.singleIdentity()))
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }
}
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

import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import nl.nuts.consent.state.ConsentRequestState
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val VALID_META_ZIP_PATH = "src/test/resources/valid_metadata.zip"

class ConsentFlowsTest {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("nl.nuts.consent.flow"),
            TestCordapp.findCordapp("nl.nuts.consent.contract")
    )))

    private val validAttachment = File(VALID_META_ZIP_PATH)

    private val a = network.createPartyNode()
    private val b = network.createPartyNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(ConsentRequestFlows.AcceptNewConsentRequest::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `signedTransaction from flow is signed by the acceptor`() {
        val signedTx = runCorrectTransaction()

        signedTx.verifySignaturesExcept(a.info.singleIdentity().owningKey)
    }

    @Test
    fun `signedTransaction from flow is signed by the initiator`() {
        val signedTx = runCorrectTransaction()

        signedTx.verifySignaturesExcept(b.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val signedTx = runCorrectTransaction()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(a, b)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has no inputs, a single output and a single attachment`() {
        val hash = a.services.attachments.importAttachment(validAttachment.inputStream(), a.info.legalIdentities.first().name.toString(), null)
        val signedTx = runCorrectTransaction(hash)

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
            assertEquals("uuid", recordedState.consentStateExternalId)
        }
    }

    @Test
    fun `attachments exist at all parties`() {
        val hash = a.services.attachments.importAttachment(validAttachment.inputStream(), a.info.legalIdentities.first().name.toString(), null)

        runCorrectTransaction(hash)

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            assertTrue(node.services.attachments.hasAttachment(hash))
        }
    }

    private fun runCorrectTransaction() : SignedTransaction {
        val hash = a.services.attachments.importAttachment(validAttachment.inputStream(), a.info.legalIdentities.first().name.toString(), null)

        return runCorrectTransaction(hash)
    }

    private fun runCorrectTransaction(attachmentId : SecureHash) : SignedTransaction {
        val flow = ConsentRequestFlows.NewConsentRequest("uuid", setOf(attachmentId), listOf(b.info.singleIdentity()))
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }
}
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
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
abstract class GenericFlowTests {
    protected val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("nl.nuts.consent.flow"),
            TestCordapp.findCordapp("nl.nuts.consent.contract")
    )))

    protected val validAttachment = File(VALID_META_ZIP_PATH)
    protected var validHash: SecureHash? = null

    protected val a = network.createPartyNode()
    protected val b = network.createPartyNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(ConsentRequestFlows.AcceptNewConsentRequest::class.java)
        }
    }

    @Before
    open fun setup() {
        network.runNetwork()
        validHash = a.services.attachments.importAttachment(validAttachment.inputStream(), a.info.legalIdentities.first().name.toString(), null)
    }

    @After
    fun tearDown() = network.stopNodes()

    abstract fun runCorrectTransaction() : SignedTransaction

    @Test
    fun `attachments exist at all parties`() {
        runCorrectTransaction()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            assertTrue(node.services.attachments.hasAttachment(validHash!!))
        }
    }

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
}
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

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.node.services.AttachmentId
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import nl.nuts.consent.contract.PartyAttachmentSignature
import nl.nuts.consent.state.ConsentRequestState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.postgresql.shaded.com.ongres.scram.common.util.CryptoUtil
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AcceptConsentRequestFlowTest : GenericFlowTests() {
    private var linearId : UniqueIdentifier? = null

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(ConsentRequestFlows.AcceptNewConsentRequest::class.java)
        }
    }

    @Before
    override fun setup() {
        super.setup()
        val signedTx = runCorrectTransaction()
        linearId = (signedTx.tx.outputs.first().data as LinearState).linearId
    }

    @Test
    fun `unknown external ID raises flow exception`() {
        val flow = ConsentRequestFlows.AcceptConsentRequest(UniqueIdentifier("uuid"), emptyList())
        val future = a.startFlow(flow)
        network.runNetwork()
        assertFailsWith(FlowException::class) {
            future.getOrThrow()
        }
    }

    @Test
    fun `recorded transaction has a single input, a single output and a single attachment`() {
        // we create a signature with the key of a Corda Party. But this must be a Nuts party (care provider)
        val attSig = PartyAttachmentSignature(a.info.legalIdentities.first(), validHash!!, a.services.keyManagementService.sign(validHash!!.bytes, a.info.legalIdentities.first().owningKey))

        val flow = ConsentRequestFlows.AcceptConsentRequest(linearId!!, listOf(attSig))
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.get()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assertEquals(1, txOutputs.size)

            val txInputs = recordedTx.tx.inputs
            assertEquals(1, txInputs.size)

            val attachments = recordedTx.tx.attachments
            assertEquals(2, attachments.size) // the first attachment is the contract and state jar

            val recordedState = txOutputs[0].data as ConsentRequestState
            assertEquals("uuid", recordedState.consentStateUUID.externalId)
        }
    }

    override fun runCorrectTransaction() : SignedTransaction {
        val flow = ConsentRequestFlows.NewConsentRequest("uuid", setOf(validHash!!), listOf(b.info.singleIdentity()))
        val future = a.startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }
}
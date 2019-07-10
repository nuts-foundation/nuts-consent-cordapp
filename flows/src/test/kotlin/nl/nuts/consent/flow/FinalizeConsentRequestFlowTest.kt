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
import net.corda.core.flows.FlowException
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.state.ConsentRequestState
import nl.nuts.consent.state.ConsentState
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinalizeConsentRequestFlowTest : GenericFlowTests() {
    private var linearId : UniqueIdentifier? = null

    @Before
    override fun setup() {
        super.setup()
        runCorrectTransaction()
    }

    @Test
    fun `unknown external ID raises flow exception`() {
        val flow = ConsentRequestFlows.FinalizeConsentRequest(UniqueIdentifier("uuid"))
        val future = a.startFlow(flow)
        network.runNetwork()
        assertFailsWith(FlowException::class) {
            future.getOrThrow()
        }
    }

    @Test
    fun `recorded transaction has a single input, a single output and a single attachment`() {
        val flow = ConsentRequestFlows.FinalizeConsentRequest(linearId!!)
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

            val recordedState = txOutputs[0].data as ConsentState
            assertEquals("uuid", recordedState.consentStateUUID.externalId)
        }
    }

    override fun runCorrectTransaction() : SignedTransaction {
        val flow = ConsentRequestFlows.NewConsentRequest("uuid", setOf(validHash!!), emptyList(), listOf(b.info.singleIdentity().name))
        val future = a.startFlow(flow)
        network.runNetwork()

        linearId = (future.get().tx.outputs.first().data as LinearState).linearId

        // accept from party a
        val attSigA = AttachmentSignature("http://nuts.nl/naming/organisation#test", validHash!!, a.services.keyManagementService.sign(validHash!!.bytes, a.info.legalIdentities.first().owningKey))
        val flowA = ConsentRequestFlows.AcceptConsentRequest(linearId!!, listOf(attSigA))
        val futureA = b.startFlow(flowA)
        network.runNetwork()
        futureA.getOrThrow()

        // accept from party b
        val attSigB = AttachmentSignature("http://nuts.nl/naming/organisation#test", validHash!!, b.services.keyManagementService.sign(validHash!!.bytes, b.info.legalIdentities.first().owningKey))
        val flowB = ConsentRequestFlows.AcceptConsentRequest(linearId!!, listOf(attSigB))
        val futureB = b.startFlow(flowB)
        network.runNetwork()
        return futureB.getOrThrow()
    }
}
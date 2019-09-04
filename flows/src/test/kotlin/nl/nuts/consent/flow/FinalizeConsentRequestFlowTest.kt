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
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.seconds
import net.corda.testing.core.singleIdentity
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.state.ConsentState
import org.h2.jdbc.JdbcSQLException
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class FinalizeConsentRequestFlowTest : GenericFlowTests() {
    private var linearId : UniqueIdentifier? = null

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `unknown external ID raises flow exception`() {
        runCorrectTransaction("id-F-1")
        val flow = ConsentFlows.FinalizeConsentRequest(UniqueIdentifier("id-F-1"))
        val future = a.startFlow(flow)
        network.runNetwork()
        assertFailsWith(FlowException::class) {
            future.getOrThrow(2.seconds)
        }
    }

    @Test
    fun `recorded transaction has a single input, a single output and a single attachment`() {
        runCorrectTransaction("id-F-2")
        val flow = ConsentFlows.FinalizeConsentRequest(linearId!!)
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow(2.seconds)

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
            assertEquals("id-F-2", recordedState.consentStateUUID.externalId)
            assertEquals(setOf(a, b).map{it.info.singleIdentity()}.toSet(), recordedState.parties)
        }
    }

    @Test
    fun `2nd transaction for the same externalId raises`() {
        // 1st
        runCorrectTransaction("id-F-3")
        val flow = ConsentFlows.FinalizeConsentRequest(linearId!!)
        val f = a.startFlow(flow)
        network.runNetwork()
        f.getOrThrow(2.seconds)


        // 2nd
        runCorrectTransaction("id-F-3")
        val future = a.startFlow(ConsentFlows.FinalizeConsentRequest(linearId!!))
        network.runNetwork()
        assertFailsWith(UnexpectedFlowEndException::class) {
            future.getOrThrow(2.seconds)
        }

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val states = node.services.vaultService.queryBy(ConsentState::class.java)
            assertEquals(1, states.states.size)

            val recordedState = states.states.first().state.data
            assertNotEquals(linearId, recordedState.consentStateUUID)

            node.transaction {
                val stmt = node.services.jdbcSession().prepareStatement("SELECT count(*) from consent_states")
                val rs = stmt.executeQuery()
                rs.next()

                assertEquals(1, rs.getInt(1))
            }
        }
    }

    @Test
    fun `existing externalId in b fails transaction started by a`() {
        runCorrectTransaction("id-F-4")
        b.transaction {
            val stmt = b.services.jdbcSession().prepareStatement("INSERT INTO consent_states VALUES(?)")
            stmt.setString(1, "id-F-4")
            stmt.execute()
        }

        val flow = ConsentFlows.FinalizeConsentRequest(linearId!!)
        val future = a.startFlow(flow)
        network.runNetwork()
        assertFailsWith(UnexpectedFlowEndException::class) {
            future.getOrThrow(2.seconds)
        }

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val states = node.services.vaultService.queryBy(ConsentState::class.java)
            assertEquals(0, states.states.size)
        }

        a.transaction {
            val stmt = a.services.jdbcSession().prepareStatement("SELECT count(*) from consent_states")
            val rs = stmt.executeQuery()
            rs.next()

            assertEquals(0, rs.getInt(1))
        }
    }

    @Test
    fun `existing externalId in a fails transaction started by a`() {
        runCorrectTransaction("id-F-5")
        a.transaction {
            val stmt = a.services.jdbcSession().prepareStatement("INSERT INTO consent_states VALUES(?)")
            stmt.setString(1, "id-F-5")
            stmt.execute()
        }

        val flow = ConsentFlows.FinalizeConsentRequest(linearId!!)
        val future = a.startFlow(flow)
        network.runNetwork()
        assertFailsWith(JdbcSQLException::class) {
            future.getOrThrow(2.seconds)
        }

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val states = node.services.vaultService.queryBy(ConsentState::class.java)
            assertEquals(0, states.states.size)
        }

        b.transaction {
            val stmt = b.services.jdbcSession().prepareStatement("SELECT count(*) from consent_states")
            val rs = stmt.executeQuery()
            rs.next()

            assertEquals(0, rs.getInt(1))
        }
    }

    override fun runCorrectTransaction(externalId: String) : SignedTransaction {
        val flow = ConsentFlows.NewConsentRequest(externalId, setOf(validHash!!), setOf("http://nuts.nl/naming/organisation#test"), setOf(b.info.singleIdentity().name))
        val future = a.startFlow(flow)
        network.runNetwork()

        linearId = (future.getOrThrow(2.seconds).tx.outputs.first().data as LinearState).linearId

        // accept from party a
        val attSigA = AttachmentSignature("http://nuts.nl/naming/organisation#test", validHash!!, a.services.keyManagementService.sign(validHash!!.bytes, a.info.legalIdentities.first().owningKey))
        val flowA = ConsentFlows.AcceptConsentRequest(linearId!!, listOf(attSigA))
        val futureA = b.startFlow(flowA)
        network.runNetwork()
        futureA.getOrThrow(2.seconds)

        // accept from party b
        val attSigB = AttachmentSignature("http://nuts.nl/naming/organisation#test", validHash!!, b.services.keyManagementService.sign(validHash!!.bytes, b.info.legalIdentities.first().owningKey))
        val flowB = ConsentFlows.AcceptConsentRequest(linearId!!, listOf(attSigB))
        val futureB = b.startFlow(flowB)
        network.runNetwork()
        return futureB.getOrThrow(2.seconds)
    }
}
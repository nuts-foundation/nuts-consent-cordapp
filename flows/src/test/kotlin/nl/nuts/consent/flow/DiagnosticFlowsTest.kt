/*
 *     Nuts consent cordapp
 *     Copyright (C) 2020 Nuts community
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

import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.`when`
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagnosticFlowsTest {

    protected val network = InternalMockNetwork(cordappsForAllNodes = listOf(
            cordappWithPackages("nl.nuts.consent.flow")
    ))

    protected val a = network.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
    protected val b = network.createNode(InternalMockNodeParameters(legalName = BOB_NAME))

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(DiagnosticFlows.PongFlow::class.java)
        }
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `PingNotaryFlow succeeds`() {
        val flow = DiagnosticFlows.PingNotaryFlow()
        val future = a.services.startFlow(flow)
        network.runNetwork()
        assertTrue(future.resultFuture.isDone)
    }

    @Test
    fun `PingRandomFlow succeeds`() {
        val flow = DiagnosticFlows.PingRandomFlow()
        val future = a.services.startFlow(flow)
        network.runNetwork()
        assertTrue(future.resultFuture.isDone)
    }

    @Test
    fun `FlowException is raised on incorrect data`() {
        val mockSession = mock<FlowSession>()
        val mockData = mock<UntrustworthyData<String>>()
        `when`(mockSession.receive<String>()).thenReturn(mockData)
        `when`(mockData.unwrap<String>(anyOrNull())).thenReturn("not pong")

        assertFailsWith(FlowException::class) {
            DiagnosticFlows.checkReceivedData(mockSession, "pong")
        }
    }
}
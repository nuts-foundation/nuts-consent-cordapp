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

import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
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

    @Before
    open fun setup() {
        network.runNetwork()
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
}
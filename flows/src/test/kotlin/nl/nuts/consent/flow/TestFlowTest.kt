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
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import nl.nuts.test.ActiveState
import nl.nuts.test.AddState
import nl.nuts.test.GenesisState
import nl.nuts.test.TestFlows
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TestFlowTest {
    val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("nl.nuts.test")
    )))

    val a = network.createPartyNode()
    val b = network.createPartyNode()

    init {
        listOf(a, b).forEach {
            //it.registerInitiatedFlow(TestFlows.IssueGenesis::class.java)
            it.registerInitiatedFlow(TestFlows.AcceptBranchFlow::class.java)
            it.registerInitiatedFlow(TestFlows.AcceptMergeFlow::class.java)
        }
    }

    @Before
    open fun setup() {
        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `test`() {
        // genesis
        val gf = TestFlows.IssueGenesis("uuid")
        val f1 = a.startFlow(gf)
        network.runNetwork()

        val gId = (f1.getOrThrow(2.seconds).tx.outputs.first().data as LinearState).linearId

        // check states
        for (node in listOf(a)) {
            assertEquals(1, node.services.vaultService.queryBy(GenesisState::class.java).states.size)
            assertEquals(0, node.services.vaultService.queryBy(AddState::class.java).states.size)
            assertEquals(0, node.services.vaultService.queryBy(ActiveState::class.java).states.size)
        }

        // branch from a, include b
        val bf = TestFlows.BranchFlow(gId, setOf(b.info.legalIdentities.first().name))
        val f2 = a.startFlow(bf)
        network.runNetwork()

        val bId = (f2.getOrThrow(2.seconds).tx.outputs.first().data as LinearState).linearId

        // check states
        for (node in listOf(a, b)) {
            //assertEquals(1, node.services.vaultService.queryBy(GenesisState::class.java).states.size) // 0 or 1
            assertEquals(1, node.services.vaultService.queryBy(AddState::class.java).states.size)
            assertEquals(0, node.services.vaultService.queryBy(ActiveState::class.java).states.size)
        }

        // merge from a
        val mf = TestFlows.MergeFlow(bId, setOf(b.info.legalIdentities.first().name))
        val futureB = a.startFlow(mf)
        network.runNetwork()
        futureB.getOrThrow(2.seconds)

        // check states
        for (node in listOf(a, b)) {
            assertEquals(0, node.services.vaultService.queryBy(GenesisState::class.java).states.size)
            assertEquals(0, node.services.vaultService.queryBy(AddState::class.java).states.size)
            assertEquals(1, node.services.vaultService.queryBy(ActiveState::class.java).states.size)
        }
    }
}
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
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import java.io.File

const val VALID_META_ZIP_PATH = "src/test/resources/valid_metadata_for_add.zip"

abstract class GenericFlowTests {
    protected val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("nl.nuts.consent.flow"),
            TestCordapp.findCordapp("nl.nuts.consent.contract")
    )))

    protected val a = network.createPartyNode()
    protected val b = network.createPartyNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(ConsentFlows.AcceptCreateConsentBranch::class.java)
            it.registerInitiatedFlow(ConsentFlows.AcceptSignConsentBranch::class.java)
            it.registerInitiatedFlow(ConsentFlows.AcceptMergeBranch::class.java)
        }
    }

    protected val validAttachment = File(VALID_META_ZIP_PATH)
    protected var validHash: SecureHash? = null

    @Before
    open fun setup() {
        network.runNetwork()
        validHash = a.services.attachments.importAttachment(validAttachment.inputStream(), a.info.legalIdentities.first().name.toString(), null)
    }

    @After
    fun tearDown() = network.stopNodes()
}
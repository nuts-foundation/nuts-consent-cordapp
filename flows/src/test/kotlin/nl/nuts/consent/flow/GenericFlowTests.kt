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
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.After
import org.junit.Before
import java.io.File

const val VALID_META_ZIP_PATH = "src/test/resources/valid_metadata_for_add.zip"
const val VALID_META_ZIP_PATH_DUP = "src/test/resources/valid_metadata_for_add_dup.zip"
const val VALID_META_ZIP_PATH2 = "src/test/resources/valid_metadata.zip"
const val VALID_META_ZIP_PATH3 = "src/test/resources/valid_metadata_for_add2.zip"

abstract class GenericFlowTests {
    protected val network = InternalMockNetwork(cordappsForAllNodes = listOf(
            cordappWithPackages("nl.nuts.consent.flow"),
            cordappWithPackages("nl.nuts.consent.schema"),
            cordappWithPackages("nl.nuts.consent.contract")
    ))

    protected val a = network.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
    protected val b = network.createNode(InternalMockNodeParameters(legalName = BOB_NAME))

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(ConsentFlows.AcceptCreateConsentBranch::class.java)
            it.registerInitiatedFlow(ConsentFlows.AcceptSignConsentBranch::class.java)
            it.registerInitiatedFlow(ConsentFlows.AcceptMergeBranch::class.java)
        }
    }

    protected var validHashAdd1: SecureHash? = null
    protected var validHashDup: SecureHash? = null
    protected var validHashUpd: SecureHash? = null
    protected var validHashAdd2: SecureHash? = null

    @Before
    open fun setup() {
        network.runNetwork()
        validHashAdd1 = a.services.attachments.importAttachment(File(VALID_META_ZIP_PATH).inputStream(), a.info.legalIdentities.first().name.toString(), null)
        validHashDup = a.services.attachments.importAttachment(File(VALID_META_ZIP_PATH_DUP).inputStream(), a.info.legalIdentities.first().name.toString(), null)
        validHashUpd = a.services.attachments.importAttachment(File(VALID_META_ZIP_PATH2).inputStream(), a.info.legalIdentities.first().name.toString(), null)
        validHashAdd2 = a.services.attachments.importAttachment(File(VALID_META_ZIP_PATH3).inputStream(), a.info.legalIdentities.first().name.toString(), null)
    }

    @After
    fun tearDown() = network.stopNodes()
}
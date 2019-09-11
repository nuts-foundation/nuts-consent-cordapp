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

package nl.nuts.consent.contract

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.io.File

const val VALID_META_ZIP_PATH_ADD = "src/test/resources/valid_metadata_for_add.zip"
const val VALID_META_ZIP_PATH_ADD2 = "src/test/resources/valid_metadata_for_add2.zip"
const val VALID_META_ZIP_PATH_UPD = "src/test/resources/valid_metadata.zip"
const val DUMMY_ZIP_PATH = "src/test/resources/dummy.zip"
const val DUMMY2_ZIP_PATH = "src/test/resources/dummy2.zip"

open class ConsentContractTest {
    val ledgerServices = MockServices()
    val homeCare = TestIdentity(CordaX500Name("homeCare", "Groenlo", "NL"))
    val generalCare = TestIdentity(CordaX500Name("GP", "Groenlo", "NL"))
    val unknownCare = TestIdentity(CordaX500Name("Shadow", "Groenlo", "NL"))
    val newAttachment = File(VALID_META_ZIP_PATH_ADD)
    val newAttachment2 = File(VALID_META_ZIP_PATH_ADD2)
    val updAttachment = File(VALID_META_ZIP_PATH_UPD)
    val dummyAttachment = File(DUMMY_ZIP_PATH)
    val unknownAttachment = File(DUMMY2_ZIP_PATH)

    val consentStateUuid = UniqueIdentifier("consentStateUuid")

    @Test
    fun `Multiple Commands require the correct number of transactions`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("The correct number of attachments are included when multiple commands are used")
            }
        }
    }

    fun createValidPAS(testIdentity: TestIdentity, hash:SecureHash) : AttachmentSignature {
        val signedBytes = Crypto.doSign(testIdentity.keyPair.private, hash.bytes)
        val signature = DigitalSignature.WithKey(testIdentity.publicKey, signedBytes)

        return AttachmentSignature("http://nuts.nl/naming/organisation#test", hash, signature)
    }

    fun createPASWrongSignature(testIdentity: TestIdentity, hash:SecureHash) : AttachmentSignature {
        val signedBytes = Crypto.doSign(testIdentity.keyPair.private, SecureHash.allOnesHash.bytes)
        val signature = DigitalSignature.WithKey(testIdentity.publicKey, signedBytes)

        return AttachmentSignature("http://nuts.nl/naming/organisation#test", hash, signature)
    }

    fun createPASWrongIdentity(testIdentity: TestIdentity, hash:SecureHash) : AttachmentSignature {
        val signedBytes = Crypto.doSign(testIdentity.keyPair.private, hash.bytes)
        val signature = DigitalSignature.WithKey(testIdentity.publicKey, signedBytes)

        return AttachmentSignature("http://nuts.nl/naming/organisation#test2", hash, signature)
    }
}
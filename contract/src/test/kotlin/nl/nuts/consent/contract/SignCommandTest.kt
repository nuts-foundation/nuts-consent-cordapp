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

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.readFully
import net.corda.testing.node.ledger
import nl.nuts.consent.state.BranchState
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.time.OffsetDateTime

class SignCommandTest : ConsentContractTest() {

    val attachmentInputStream = newAttachment.inputStream()
    val attHash = attachmentInputStream.readFully().sha256()
    val cb = ConsentBranch(
        uuid = consentStateUuid,
        branchPoint = consentStateUuid,
        attachments = setOf(attHash),
        legalEntities = setOf("http://nuts.nl/naming/organisation#test"),
        signatures = emptyList(),
        parties = setOf(homeCare.party, generalCare.party)
    )

    @Test
    fun `SignCommand valid transaction`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                verifies()
            }
        }
    }

    @Test
    fun `SignCommand requires Open txInput`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(state = BranchState.Closed)
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("Input state does have an open state")
            }
        }
    }

    @Test
    fun `SignCommand requires same initiatingNode`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)), initiatingNode = "node")
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("initiatingNode remains the same")
            }
        }
    }

    @Test
    fun `SignCommand requires same initiatingLegalEntity`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)), initiatingLegalEntity = "entity")
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("initiatingLegalEntity remains the same")
            }
        }
    }

    @Test
    fun `SignCommand requires same branchTime`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)), branchTime = OffsetDateTime.MAX)
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("branchTime remains the same")
            }
        }
    }

    @Test
    fun `SignCommand leaves list of legalEntities the same`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(legalEntities = emptySet())
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("legalEntities remain the same")
            }
        }
    }

    @Test
    fun `SignCommand invalid legalEntityURI`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createPASWrongIdentity(homeCare, attHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("unknown legalEntity found in attachmentSignatures, not present in attachments")
            }
        }
    }

    @Test
    fun `SignCommand transaction must have same set of attachments as output state`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, attHash)), attachments = emptySet())
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("Attachments in state have the same amount as include in the transaction")
            }
        }
    }

    @Test
    fun `SignCommand only creates ConsentBranch states`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    ConsentState(consentStateUuid, 1, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("1 ConsentBranch is produced")
            }
        }
    }

    @Test
    fun `SignCommand requires more signatures on output than input`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("1 more signature is present")
            }
        }
    }

    @Test
    fun `SignCommand requires list of AttachmentSignatures that match attachments`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(homeCare, SecureHash.allOnesHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("All signatures belong to attachments")
            }
        }
    }

    @Test
    fun `SignCommand requires unique set of AttachmentSignatures`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(generalCare, attHash)))
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createValidPAS(generalCare, attHash), createValidPAS(generalCare, attHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("All attachment signatures are unique")
            }
        }
    }

    @Test
    fun `SignCommand requires valid set of AttachmentSignatures`() {
        ledgerServices.ledger {
            attachment(newAttachment.inputStream())

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    cb
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    cb.copy(signatures = listOf(createPASWrongSignature(homeCare, attHash)))
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.SignCommand()
                )
                `fails with`("All signatures are valid")
            }
        }
    }
}
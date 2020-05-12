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
import net.corda.testing.node.ledger
import nl.nuts.consent.state.BranchState
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test

class SignCommandTest : ConsentContractTest() {
    @Test
    fun `SignCommand requires Open txInput`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party), BranchState.Closed)
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                        listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party), BranchState.Open, "node")
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                        listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                    ConsentContract.CONTRACT_ID,
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party), BranchState.Open, "", "entity")
                )
                output(
                    ConsentContract.CONTRACT_ID,
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                        listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
    fun `SignCommand leaves list of legalEntities the same`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createPASWrongIdentity(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, emptySet(), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
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
    fun `SignCommand requires more attachments on output than input`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createValidPAS(homeCare, SecureHash.allOnesHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createValidPAS(generalCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"),
                                listOf(createPASWrongSignature(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
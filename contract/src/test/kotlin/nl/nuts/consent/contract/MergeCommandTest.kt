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
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test

class MergeCommandTest : ConsentContractTest() {
    @Test
    fun `MergeCommand valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("a", "b"),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                verifies()
            }
        }
    }

    @Test
    fun `MergeCommand can't be combined with ther commands`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("a", "b"),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("Commands with requireSingleCommand=true must run in a standalone transaction")
            }
        }
    }

    @Test
    fun `MergeCommand fails for wrong version number`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("a", "b"),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 3, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("Version number is 1 more")
            }
        }
    }

    @Test
    fun `MergeCommand with missing attachments`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("Attachments include new attachments")
            }
        }
    }

    @Test
    fun `MergeCommand with missing attachment in final state`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("Attachments in state have the same amount as include in the transaction")
            }
        }
    }

    @Test
    fun `MergeCommand requires complete list of attachmentSignatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("All signature are present")
            }
        }
    }

    @Test
    fun `MergeCommand all signatures belong to attachments`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("a", "b"),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(homeCare, SecureHash.zeroHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("All signatures belong to attachments")
            }
        }
    }

    @Test
    fun `MergeCommand All signatures are unique`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("a", "b"),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("All attachment signatures are unique")
            }
        }
    }

    @Test
    fun `MergeCommand requires valid set of signatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("a", "b"),
                                listOf(createPASWrongSignature(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.MergeCommand()
                )
                `fails with`("All signatures are valid")
            }
        }
    }

    @Test
    fun `SignCommand valid transaction`() {
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
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
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
}
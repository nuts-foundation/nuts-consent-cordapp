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

import net.corda.testing.node.ledger
import nl.nuts.consent.state.BranchState
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test

/**
 * Includes requirement tests from BranchCommand as well
 */
class AddCommandTest : ConsentContractTest() {
    @Test
    fun `AddCommand invalid attachment`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("Attachment is missing required metadata file")
            }
        }
    }

    @Test
    fun `AddCommand all participants as signers`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("All new participants must be signers")
            }
        }
    }

    @Test
    fun `AddCommand must have 1 input`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party, generalCare.party))
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
                `fails with`("The right amount of states are consumed")
            }
        }
    }

    @Test
    fun `AddCommand must have 1 ConsentState output`() {
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
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
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
                `fails with`("1 ConsentState is produced")
            }
        }
    }

    @Test
    fun `AddCommand must have correct number of output states`() {
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
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("The right amount of states are created")
            }
        }
    }

    @Test
    fun `AddCommand must have at least 1 attachment`() {
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
                        ConsentBranch(consentStateUuid, consentStateUuid, emptySet(), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("ConsentBranch must add new attachments")
            }
        }
    }

    @Test
    fun `AddCommand must have at least 1 attachment (2)`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(attHash), setOf(homeCare.party, generalCare.party))
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
                `fails with`("ConsentBranch must add new attachments")
            }
        }
    }

    @Test
    fun `AddCommand must include the attachment`() {
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
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("There must at least be 1 attachment")
            }
        }
    }

    @Test
    fun `AddCommand must have same legalEntities as listed in metadata`() {
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
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("legal entities in attachments do not match those in ConsentBranch")
            }
        }
    }

    @Test
    fun `AddCommand must have same legalEntities as listed in metadata 2`() {
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
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test", "b"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("legal entities in attachments do not match those in ConsentBranch")
            }
        }
    }

    @Test
    fun `AddCommand valid transaction`() {
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
                verifies()
            }
        }
    }

    @Test
    fun `AddCommand has open branch as output`() {
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
                    ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party), BranchState.Closed)
                )
                attachment(attHash)
                command(
                    listOf(homeCare.publicKey, generalCare.publicKey),
                    ConsentContract.ConsentCommands.AddCommand()
                )
                `fails with`("Output branch does have an open state")
            }
        }
    }

    @Test
    fun `AddCommand fails if version number is incorrect`() {
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
                        ConsentState(consentStateUuid, 3, emptySet(), setOf(homeCare.party, generalCare.party))
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
                `fails with`("Version number is 1 more")
            }
        }
    }
}
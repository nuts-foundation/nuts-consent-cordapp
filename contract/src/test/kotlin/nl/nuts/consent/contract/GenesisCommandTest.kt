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
import nl.nuts.consent.state.ConsentState
import org.junit.Test

class GenesisCommandTest : ConsentContractTest() {
    @Test
    fun `GenesisCommand verifies for correct input and output`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party))
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                verifies()
            }
        }
    }

    @Test
    fun `Genesis commands can't be combined with other commands`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party))
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                `fails with`("Commands with requireSingleCommand=true must run in a standalone transaction")
            }
        }
    }

    @Test
    fun `GenesisCommand fails with incorrect version number`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party))
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                `fails with`("Version number is 1")
            }
        }
    }

    @Test
    fun `GenesisCommand fails if input states are added`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party))
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                `fails with`("The right amount of states are consumed")
            }
        }
    }

    @Test
    fun `GenesisCommand fails on incorrect output states`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, emptySet(), setOf(homeCare.party))
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                `fails with`("The right amount of states are created")
            }
        }
    }

    @Test
    fun `OutputState in transaction with GenesisCommand may not have attachments`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, setOf(SecureHash.zeroHash), setOf(homeCare.party))
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                `fails with`("The output state does not have any attachments")
            }
        }
    }

    @Test
    fun `GenesisCommand does not allow additional attachments`() {
        ledgerServices.ledger {
            val attachmentInputStream = newAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, emptySet(), setOf(homeCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenesisCommand()
                )
                `fails with`("The transaction does not have any additional attachments")
            }
        }
    }
}
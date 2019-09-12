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
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test

class UpdateCommandTest : ConsentContractTest() {
    @Test
    fun `UpdateCommand valid transaction`() {
        ledgerServices.ledger {
            val oldAttHash = attachment(newAttachment.inputStream())
            val updAttHash = attachment(updAttachment.inputStream())

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, setOf(oldAttHash), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(oldAttHash), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(updAttHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(updAttHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.UpdateCommand()
                )
                verifies()
            }
        }
    }

    @Test
    fun `UpdateCommand fails on missing referenced attachment in input state`() {
        ledgerServices.ledger {
            val oldAttHash = attachment(newAttachment2.inputStream())
            val updAttHash = attachment(updAttachment.inputStream())

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, setOf(oldAttHash), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(oldAttHash), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(updAttHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(updAttHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.UpdateCommand()
                )
                `fails with`("attachment referenced by new attachment must be present in input state")
            }
        }
    }

    @Test
    fun `UpdateCommand fails if no attachments exist that is an actual update`() {
        ledgerServices.ledger {
            val hash1 = attachment(newAttachment2.inputStream())
            val hash2 = attachment(newAttachment.inputStream())

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 1, setOf(hash1), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, 2, setOf(hash1), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(hash1, hash2), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(hash1)
                attachment(hash2)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.UpdateCommand()
                )
                `fails with`("at least 1 update exists in attachment list")
            }
        }
    }
}
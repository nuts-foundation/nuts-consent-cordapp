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
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import nl.nuts.consent.state.ConsentRequestState
import nl.nuts.consent.state.ConsentState
import org.junit.Test

// this file includes some example tests, they do not really have something yet to do with consent functionality
class ConsentContractTest {
    private val ledgerServices = MockServices()
    private val homeCare = TestIdentity(CordaX500Name("homeCare", "Groenlo", "NL"))
    private val generalCare = TestIdentity(CordaX500Name("GP", "Groenlo", "NL"))

    //@Nested junit 5 required for this
    //inner class ConsentRequestState {

    @Test
    fun `valid transaction for new ConsentRequestState`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `createNewRequest transaction must have unique set of participants`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("All participants are unique")
            }
        }
    }

    @Test
    fun `createNewRequest transaction must include all participants as signers`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("All participants must be signers")
            }
        }
    }

    @Test
    fun `createNewRequest transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("The right amount of states are consumed")
            }
        }
    }

    @Test
    fun `createNewRequest transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("The right amount of states are created")
            }
        }
    }

    @Test
    fun `createNewRequest transaction must only have ConsentRequestState as output`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("Only ConsentRequestStates are created")
            }
        }
    }

    @Test
    fun `createNewRequest transaction must at least have 1 signed attachment`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment( // unsigned
                        ConsentContract.CONTRACT_ID,
                        SecureHash.allOnesHash
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("All attachments must be signed by all participants")
            }
        }
    }

    @Test
    fun `valid transaction for accepted request`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `AcceptRequest only consumes ConsentRequestStates`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("Only ConsentRequestStates are consumed")
            }
        }
    }

    @Test
    fun `AcceptRequest only consumes a single ConsentRequestState`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("The right amount of states are consumed")
            }
        }
    }

    @Test
    fun `AcceptRequest only produces ConsentStates`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState("consentStateUuid", listOf(homeCare.party, generalCare.party))
                )
                attachment(
                        ConsentContract.CONTRACT_ID,
                        SecureHash.randomSHA256(),
                        listOf(homeCare.party, generalCare.party).map{ it.owningKey }
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("Only ConsentStates are created")
            }
        }
    }
}
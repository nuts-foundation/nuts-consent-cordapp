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
import nl.nuts.consent.state.ConsentRequestState
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.io.File

const val VALID_META_ZIP_PATH = "src/test/resources/valid_metadata.zip"
const val DUMMY_ZIP_PATH = "src/test/resources/dummy.zip"
const val DUMMY2_ZIP_PATH = "src/test/resources/dummy2.zip"

class ConsentContractTest {
    private val ledgerServices = MockServices()
    private val homeCare = TestIdentity(CordaX500Name("homeCare", "Groenlo", "NL"))
    private val generalCare = TestIdentity(CordaX500Name("GP", "Groenlo", "NL"))
    private val unknownCare = TestIdentity(CordaX500Name("Shadow", "Groenlo", "NL"))
    private val validAttachment = File(VALID_META_ZIP_PATH)
    private val dummyAttachment = File(DUMMY_ZIP_PATH)
    private val unknownAttachment = File(DUMMY2_ZIP_PATH)

    private val consentStateUuid = UniqueIdentifier("consentStateUuid")

    @Test
    fun `GenericRequest valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.GenericRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `GenericRequest invalid attachment`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.GenericRequest()
                )
                `fails with`("Attachment is missing required metadata file")
            }
        }
    }

    @Test
    fun `GenericRequest must include all participants as signers`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey),
                        ConsentContract.ConsentCommands.GenericRequest()
                )
                `fails with`("All participants must be signers")
            }
        }
    }

    @Test
    fun `GenericRequest must have one output`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.GenericRequest()
                )
                `fails with`("The right amount of states are created")
            }
        }
    }

    @Test
    fun `GenericRequest must at least have 1 attachment`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.GenericRequest()
                )
                `fails with`("There must at least be 1 attachment")
            }
        }
    }

    @Test
    fun `CreateRequest valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `CreateRequest must have no inputs`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("The right amount of states are consumed")
            }
        }
    }

    @Test
    fun `CreateRequest must only have ConsentRequestState as output`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("Only ConsentRequestStates are created")
            }
        }
    }

    @Test
    fun `CreateRequest must at least have 1 attachment in state`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, emptySet(), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("Attachments in state have the same amount as included in the transaction")
            }
        }
    }

    @Test
    fun `CreateRequest attachments must be the same for state and transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            val unknownAttachmentInputStream = unknownAttachment.inputStream()
            val unknownAttHash = attachment(unknownAttachmentInputStream)

            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(unknownAttHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.CreateRequest()
                )
                `fails with`("All attachments in state are include in the transaction")
            }
        }
    }

    @Test
    fun `AcceptRequest attachments must be the same for state and transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            val unknownAttachmentInputStream = unknownAttachment.inputStream()
            val unknownAttHash = attachment(unknownAttachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(unknownAttHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("All attachments in state are include in the transaction")
            }
        }
    }

    @Test
    fun `ProcessRequest valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.ProcessRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `ProcessRequest only consumes ConsentRequestStates`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.ProcessRequest()
                )
                `fails with`("Only ConsentRequestStates are consumed")
            }
        }
    }

    @Test
    fun `ProcessRequest only consumes a single ConsentRequestState`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.ProcessRequest()
                )
                `fails with`("The right amount of states are consumed")
            }
        }
    }

    @Test
    fun `FinalizeRequest valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `FinalizeRequest with missing attachments in out state`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("Attachments on output state are the same as input state")
            }
        }
    }

    @Test
    fun `FinalizeRequest attachments must be the same for state and transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            val unknownAttachmentInputStream = unknownAttachment.inputStream()
            val unknownAttHash = attachment(unknownAttachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(unknownAttHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("All attachments in state are include in the transaction")
            }
        }
    }

    @Test
    fun `FinalizeRequest only produces ConsentStates`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("Only ConsentStates are created")
            }
        }
    }

    @Test
    fun `FinalizeRequest requires complete list of AttachmentSignatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("All signatures are present")
            }
        }
    }

    @Test
    fun `FinalizeRequest requires list of AttachmentSignatures that match attachments`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, SecureHash.allOnesHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("All signatures belong to attachments")
            }
        }
    }

    @Test
    fun `FinalizeRequest requires unique set of AttachmentSignatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(generalCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("All attachment signatures are unique")
            }
        }
    }

    @Test
    fun `FinalizeRequest requires valid set of AttachmentSignatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createPASWrongSignature(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("All signatures are valid")
            }
        }
    }

    @Test
    fun `FinalizeRequest transaction must have same set of attachments as output state`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, emptySet(), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("Attachments in state have the same amount as include in the transaction")
            }
        }
    }

    @Test
    fun `FinalizeRequest only accepts AttachmentSignatures from involved parties`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(unknownCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.FinalizeRequest()
                )
                `fails with`("All signatures are present")
            }
        }
    }

    @Test
    fun `AcceptRequest valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                verifies()
            }
        }
    }

    @Test
    fun `ProcessRequest invalid legalEntityURI`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createPASWrongIdentity(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("unknown legalEntity found in attachmentSignatures, not present in attachments")
            }
        }
    }

    @Test
    fun `AcceptRequest transaction must have same set of attachments as output state`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, emptySet(), emptySet(),
                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("Attachments in state have the same amount as include in the transaction")
            }
        }
    }

    @Test
    fun `AcceptRequest only creates ConsentRequestStates`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("Only ConsentRequestStates are created")
            }
        }
    }

    @Test
    fun `AcceptRequest requires more attachments on output than input`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("Output state has [attachments] more signatures than input state")
            }
        }
    }

    @Test
    fun `AcceptRequest requires list of AttachmentSignatures that match attachments`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(homeCare, SecureHash.allOnesHash)), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("All signatures belong to attachments")
            }
        }
    }

    @Test
    fun `AcceptRequest requires unique set of AttachmentSignatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createValidPAS(generalCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("All attachment signatures are unique")
            }
        }
    }

    @Test
    fun `AcceptRequest requires valid set of AttachmentSignatures`() {
        ledgerServices.ledger {
            val attachmentInputStream = dummyAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentRequestState(consentStateUuid, setOf(attHash), emptySet(),
                                listOf(createPASWrongSignature(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
                )
                attachment(attHash)
                command(
                        listOf(homeCare.publicKey, generalCare.publicKey),
                        ConsentContract.ConsentCommands.AcceptRequest()
                )
                `fails with`("All signatures are valid")
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
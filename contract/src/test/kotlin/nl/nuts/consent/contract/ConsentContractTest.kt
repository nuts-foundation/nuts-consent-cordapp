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
    fun `GenesisCommand verifies for correct input and output`() {
        ledgerServices.ledger {
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party))
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
    fun `GenesisCommand fails if input states are added`() {
        ledgerServices.ledger {
            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party))
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
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party))
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
                        ConsentState(consentStateUuid, setOf(SecureHash.zeroHash), setOf(homeCare.party))
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
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)
            transaction {
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party))
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

//    @Test
//    fun `GenericRequest valid transaction`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                verifies()
//            }
//        }
//    }
//
//    @Test
//    fun `GenericRequest invalid attachment`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                `fails with`("Attachment is missing required metadata file")
//            }
//        }
//    }
//
//    @Test
//    fun `GenericRequest must include all participants as signers`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                `fails with`("All participants must be signers")
//            }
//        }
//    }
//
//    @Test
//    fun `GenericRequest must have one output`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                `fails with`("The right amount of states are created")
//            }
//        }
//    }
//
//    @Test
//    fun `GenericRequest must at least have 1 attachment`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                `fails with`("There must at least be 1 attachment")
//            }
//        }
//    }
//
//    @Test
//    fun `GenericRequest must have same legalEntities as listed in metadata`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                `fails with`("legal entities in attachments do not match those in consentRequestState")
//            }
//        }
//    }
//
//    @Test
//    fun `GenericRequest must have same legalEntities as listed in metadata 2`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test", "b"), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.GenericCommand()
//                )
//                `fails with`("legal entities in attachments do not match those in consentRequestState")
//            }
//        }
//    }
//
    @Test
    fun `AddCommand valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
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
//
//    @Test
//    fun `CreateRequest must have no inputs`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.CreateCommand()
//                )
//                `fails with`("The right amount of states are consumed")
//            }
//        }
//    }
//
//    @Test
//    fun `CreateRequest must only have ConsentRequestState as output`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.CreateCommand()
//                )
//                `fails with`("Only ConsentRequestStates are created")
//            }
//        }
//    }
//
//    @Test
//    fun `CreateRequest must at least have 1 attachment in state`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, emptySet(), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.CreateCommand()
//                )
//                `fails with`("Attachments in state have the same amount as included in the transaction")
//            }
//        }
//    }
//
//    @Test
//    fun `CreateRequest attachments must be the same for state and transaction`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            val unknownAttachmentInputStream = unknownAttachment.inputStream()
//            val unknownAttHash = attachment(unknownAttachmentInputStream)
//
//            transaction {
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(unknownAttHash), setOf("http://nuts.nl/naming/organisation#test"), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.CreateCommand()
//                )
//                `fails with`("All attachments in state are include in the transaction")
//            }
//        }
//    }
//
//    @Test
//    fun `ProcessRequest valid transaction`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.ProcessCommand()
//                )
//                verifies()
//            }
//        }
//    }
//
//    @Test
//    fun `ProcessRequest only consumes ConsentRequestStates`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.ProcessCommand()
//                )
//                `fails with`("Only ConsentRequestStates are consumed")
//            }
//        }
//    }
//
//    @Test
//    fun `ProcessRequest only consumes a single ConsentRequestState`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.ProcessCommand()
//                )
//                `fails with`("The right amount of states are consumed")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest valid transaction`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                verifies()
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest with missing attachments in out state`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, emptySet(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("Attachments on output state are the same as input state")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest only produces ConsentStates`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = dummyAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("Only ConsentStates are created")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest requires complete list of AttachmentSignatures`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(homeCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("All signatures are present")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest requires list of AttachmentSignatures that match attachments`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(homeCare, SecureHash.allOnesHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("All signatures belong to attachments")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest requires unique set of AttachmentSignatures`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(generalCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("All attachment signatures are unique")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest requires valid set of AttachmentSignatures`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createPASWrongSignature(homeCare, attHash), createValidPAS(generalCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("All signatures are valid")
//            }
//        }
//    }
//
//    @Test
//    fun `FinalizeRequest only accepts AttachmentSignatures from involved parties`() {
//        ledgerServices.ledger {
//            val attachmentInputStream = validAttachment.inputStream()
//            val attHash = attachment(attachmentInputStream)
//
//            transaction {
//                input(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentBranch(consentStateUuid, setOf(attHash), emptySet(),
//                                listOf(createValidPAS(unknownCare, attHash)), setOf(homeCare.party, generalCare.party))
//                )
//                output(
//                        ConsentContract.CONTRACT_ID,
//                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
//                )
//                attachment(attHash)
//                command(
//                        listOf(homeCare.publicKey, generalCare.publicKey),
//                        ConsentContract.ConsentCommands.FinalizeCommand()
//                )
//                `fails with`("All signatures are present")
//            }
//        }
//    }

    @Test
    fun `SignCommand valid transaction`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
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

    @Test
    fun `SignCommand leaves list of legalEntities the same`() {
        ledgerServices.ledger {
            val attachmentInputStream = validAttachment.inputStream()
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
            val attachmentInputStream = validAttachment.inputStream()
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
            val attachmentInputStream = validAttachment.inputStream()
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
            val attachmentInputStream = validAttachment.inputStream()
            val attHash = attachment(attachmentInputStream)

            transaction {
                input(
                        ConsentContract.CONTRACT_ID,
                        ConsentBranch(consentStateUuid, consentStateUuid, setOf(attHash), emptySet(), emptyList(), setOf(homeCare.party, generalCare.party))
                )
                output(
                        ConsentContract.CONTRACT_ID,
                        ConsentState(consentStateUuid, setOf(attHash), setOf(homeCare.party, generalCare.party))
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
            val attachmentInputStream = validAttachment.inputStream()
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
            val attachmentInputStream = validAttachment.inputStream()
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
            val attachmentInputStream = validAttachment.inputStream()
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
            val attachmentInputStream = validAttachment.inputStream()
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
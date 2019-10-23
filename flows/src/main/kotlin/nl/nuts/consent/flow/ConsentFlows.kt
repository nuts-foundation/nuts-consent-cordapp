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

package nl.nuts.consent.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.contract.ConsentContract
import nl.nuts.consent.contract.ConsentContract.Companion.extractMetadata
import nl.nuts.consent.state.ConsentBase
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import java.util.*

/**
 * Collection of ConsentRequest flows: New, Accept, Finalize with their counter parties
 */
object ConsentFlows {

    fun referencedAttachments(state : ConsentBranch, serviceHub: ServiceHub) : Set<SecureHash> {
        return state.referencedAttachments(state.attachments.map { it to serviceHub.attachments.openAttachment(it)!! }.toMap())
    }

    private fun containedConsentRecordHash(state : ConsentBase, serviceHub: ServiceHub) : Set<String> {
        return state.attachments.map {
            val att = serviceHub.attachments.openAttachment(it)!!
            val metadata = extractMetadata(att)
            metadata.consentRecordHash
        }.toSet()
    }

    /**
     * A check for duplicate attachments, this is done outside the contract because the contents of the attachments is required
     */
    fun raiseOnDuplicateConsentRecords(consentState: ConsentState, consentBranch: ConsentBranch, serviceHub: ServiceHub, side:String = "origin") {
        val consentRecordHashInState = containedConsentRecordHash(consentState, serviceHub)
        val consentRecordHashInBranch = containedConsentRecordHash(consentBranch, serviceHub)

        if (consentRecordHashInBranch.intersect(consentRecordHashInState).isNotEmpty()) {
            throw FlowException("ConsentBranch contains 1 or more records already present in ConsentState, detected at $side")
        }
    }

    /**
     * Flow for creating the genesis record for a subj/actor pair at a custodian
     */
    @StartableByRPC
    class CreateGenesisConsentState(val externalId:String)  : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction for new genesis block.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : Step("Recording transaction in the vault.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val consentState = ConsentState(UniqueIdentifier(externalId), 1, emptySet(), setOf(serviceHub.myInfo.legalIdentities.first()))
            val txCommand = Command(ConsentContract.ConsentCommands.GenesisCommand(), consentState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(consentState, ConsentContract.CONTRACT_ID)
                    .addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            //store(consentState.uuid)

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(partSignedTx, emptySet<FlowSession>(), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    /**
     * Flow for creating a new ConsentBranch than can then be accepted by other parties
     *
     * @param consentStateUuid the existing state uuid
     * @param attachments hashes of attachments containing the consent records
     * @param legalEntities list of involved parties, also present in metadata. This is just to help other parts of the node to quickly filter if action is needed
     * @param parties Other nodes involved in transaction (excluding this one)
     */
    @InitiatingFlow
    @StartableByRPC
    open class CreateConsentBranch(val consentBranchUUID: UUID, val consentStateUuid:UniqueIdentifier, val attachments: Set<SecureHash>, val legalEntities: Set<String>, val peers: Set<CordaX500Name>) : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
            object FINDING_PREVIOUS_STATE : Step("Finding previous state to consume (Genesis or existing consent).")
            object GENERATING_TRANSACTION : Step("Generating transaction based on new consent request.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object SENDING_DATA : Step("Sending required data for other parties to be able to verify.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    FINDING_PREVIOUS_STATE,
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    SENDING_DATA,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // names to Party
            val parties = peers.map{
                serviceHub.networkMapCache.getPeerByLegalName(it) ?: throw FlowException("Unknown peer node: ${it.commonName}")
            }.toMutableSet()

            parties.remove(serviceHub.myInfo.legalIdentities.first())

            progressTracker.currentStep = FINDING_PREVIOUS_STATE
            val criteria = QueryCriteria.LinearStateQueryCriteria(
                    linearId = listOf(consentStateUuid),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(ConsentState::class.java))
            val pages: Vault.Page<ConsentState> = serviceHub.vaultService.queryBy(criteria = criteria)

            if (pages.states.size != 1) {
                throw FlowException("Given UniqueIdentifier not found or does not have the correct amount of states (found = ${pages.states.size})")
            }

            val consentStateRef = pages.states.first()
            val newConsentState = consentStateRef.state.data.copy(version = consentStateRef.state.data.version + 1)

            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val newState = ConsentBranch(UniqueIdentifier(id = consentBranchUUID, externalId = consentStateUuid.externalId), consentStateUuid, attachments, legalEntities, emptyList(), parties + serviceHub.myInfo.legalIdentities.first())
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(consentStateRef)
                    .addOutputState(newState, ConsentContract.CONTRACT_ID)
                    .addOutputState(newConsentState, ConsentContract.CONTRACT_ID)

            // Select correct commands
            if (hasNew()) {
                txBuilder.addCommand(Command(ConsentContract.ConsentCommands.AddCommand(), newState.participants.map { it.owningKey }))
            }

            val referencedAttachmentsInBranch = referencedAttachments(newState, serviceHub)
            if (referencedAttachmentsInBranch.isNotEmpty()) {
                txBuilder.addCommand(Command(ConsentContract.ConsentCommands.UpdateCommand(), newState.participants.map { it.owningKey }))
            }

            // add all attachments to transaction
            attachments.forEach { txBuilder.addAttachment(it) }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid according to contract.
            txBuilder.verify(serviceHub)
            // Compare consentRecordHash of new and existing records
            raiseOnDuplicateConsentRecords(consentStateRef.state.data, newState, serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = SENDING_DATA
            var otherPartySessions = parties.map { initiateFlow(it) }
            otherPartySessions.map { subFlow(SendStateAndRefFlow(it, listOf(consentStateRef))) }

            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
        }

        protected fun hasNew() : Boolean {
            this.attachments.forEach {
                val att = serviceHub.attachments.openAttachment(it) ?: throw FlowException("Unknown attachment $it")

                val metadata = extractMetadata(att)
                if (metadata.previousAttachmentId == null) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Counter party flow for CreateConsentBranch
     * All checks are done within the contract.
     */
    @InitiatedBy(CreateConsentBranch::class)
    class AcceptCreateConsentBranch(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            // also resolves tx history
            val untrustworthyData = subFlow(ReceiveStateAndRefFlow<ConsentState>(askingPartySession))

            requireThat {
                "a single stateRef has been send" using (untrustworthyData.size == 1)
            }

            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    requireThat {
                        "a single ConsentState is consumed" using (stx.inputs.size == 1)
                        "a single ConsentBranch is produced" using (stx.coreTransaction.outRefsOfType<ConsentBranch>().size == 1)
                        "the received ref equals the input ref" using (untrustworthyData[0].ref == stx.inputs[0])
                    }
                    val consentBranch = stx.coreTransaction.outRefsOfType<ConsentBranch>()[0].state.data

                    // check for duplicates at other nodes as well
                    raiseOnDuplicateConsentRecords(untrustworthyData[0].state.data, consentBranch, serviceHub, "counter party")
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }

    }

    @InitiatingFlow
    @StartableByRPC
    class SignConsentBranch(val consentBranchUUID:UniqueIdentifier, val approvedSigs: List<AttachmentSignature>) : FlowLogic<SignedTransaction>() {

        companion object {
            object FIND_CURRENT_STATE : Step("Finding existing ConsentBranch record.")
            object GENERATING_TRANSACTION : Step("Generating transaction based on existing consent request.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    FIND_CURRENT_STATE,
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // identity of this node
            val me = serviceHub.myInfo.legalIdentities.first()

            // Stage 0.
            progressTracker.currentStep = FIND_CURRENT_STATE
            val criteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(me),
                    linearId = listOf(consentBranchUUID),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(ConsentBranch::class.java))

            val pages: Vault.Page<ConsentBranch> = serviceHub.vaultService.queryBy(criteria = criteria)

            if (pages.states.size != 1) {
                throw FlowException("Given external ID does not have any unconsumed states")
            }

            val currentStateRef = pages.states.first()
            val currentState = currentStateRef.state.data
            val newState = currentState.copy(signatures = currentState.signatures + approvedSigs)

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(ConsentContract.ConsentCommands.SignCommand(), newState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(currentStateRef)
                    .addOutputState(newState, ConsentContract.CONTRACT_ID)
                    .addCommand(txCommand)

            // add all previous attachments to transaction
            currentState.attachments.forEach { txBuilder.addAttachment(it) }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            var otherPartySessions = (newState.participants - me).map { initiateFlow(it) }
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    /**
     * Counter party flow for SignConsentBranch
     * All checks are done within the contract.
     */
    @InitiatedBy(SignConsentBranch::class)
    class AcceptSignConsentBranch(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {

                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }

    }

    /**
     * Flow for mering a ConsentBranch, creating a newer ConsentState
     *
     * @param consentStateUUID the unique identifier used in the ConsentRequestState
     */
    @InitiatingFlow
    @StartableByRPC
    class MergeBranch(val branchUUID:UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
            object FIND_CURRENT_BRANCH : Step("Finding existing ConsentBranch record.")
            object FIND_CURRENT_STATE : Step("Finding existing ConsentState record.")
            object FIND_REFERENCED_ATTACHMENTS : Step("Finding updated attachments.")
            object GENERATING_TRANSACTION : Step("Generating transaction based on existing consent request.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    FIND_CURRENT_BRANCH,
                    FIND_CURRENT_STATE,
                    FIND_REFERENCED_ATTACHMENTS,
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // identity of this node
            val me = serviceHub.myInfo.legalIdentities.first()

            progressTracker.currentStep = FIND_CURRENT_BRANCH
            val branchRef = findBranchState(branchUUID)
            val branchState = branchRef.state.data

            progressTracker.currentStep = FIND_CURRENT_STATE
            val consentRef = findConsentState(branchRef.state.data.branchPoint)
            val consentState = consentRef.state.data

            progressTracker.currentStep = FIND_REFERENCED_ATTACHMENTS
            val referenceAttachments = referencedAttachments(branchState, serviceHub)
            val consentStateAttachmentsToKeep = consentState.attachments.filter { !referenceAttachments.contains(it) }.toSet()

            val newState = ConsentState(
                    uuid = consentRef.state.data.linearId,
                    version = consentState.version + 1,
                    attachments = consentStateAttachmentsToKeep + branchState.attachments,
                    parties = branchState.parties)

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(ConsentContract.ConsentCommands.MergeCommand(), newState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(consentRef)
                    .addInputState(branchRef)
                    .addOutputState(newState, ConsentContract.CONTRACT_ID)
                    .addCommand(txCommand)

            // add all new attachments to transaction
            branchState.attachments.forEach { txBuilder.addAttachment(it) }
            // and the leftovers
            consentStateAttachmentsToKeep.forEach { txBuilder.addAttachment(it) }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            var otherPartySessions = (newState.participants - me).map { initiateFlow(it) }

            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
        }

        private fun findBranchState(branchUUID: UniqueIdentifier) : StateAndRef<ConsentBranch> {
            val criteria = QueryCriteria.LinearStateQueryCriteria(
                    linearId = listOf(branchUUID),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(ConsentBranch::class.java))
            val pages: Vault.Page<ConsentBranch> = serviceHub.vaultService.queryBy(criteria = criteria)
            if (pages.states.size != 1) {
                throw FlowException("Given external ID does not have the correct amount of unconsumed branch states")
            }

            return pages.states.first()
        }

        private fun findConsentState(branchUUID: UniqueIdentifier) : StateAndRef<ConsentState> {
            val criteria = QueryCriteria.LinearStateQueryCriteria(
                    linearId = listOf(branchUUID),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(ConsentState::class.java))
            val pages: Vault.Page<ConsentState> = serviceHub.vaultService.queryBy(criteria = criteria)
            if (pages.states.size != 1) {
                throw FlowException("Given external ID does not have the correct amount of unconsumed consent states")
            }

            return pages.states.first()
        }
    }

    /**
     * Counter party flow for MergeBranch
     * All checks are done within the contract.
     */
    @InitiatedBy(MergeBranch::class)
    class AcceptMergeBranch(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {

                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }
    }
}
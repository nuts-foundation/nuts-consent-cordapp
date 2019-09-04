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
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.node.internal.AbstractNode
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import nl.nuts.consent.contract.ConsentContract
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import nl.nuts.test.AddState
import nl.nuts.test.GenesisState
import nl.nuts.test.TestFlows

/**
 * Collection of ConsentRequest flows: New, Accept, Finalize with their counter parties
 */
object ConsentFlows {

    /**
     * Flow for creating the genesis record for a subj/actor pair at a custodian
     */
    @InitiatingFlow
    @StartableByRPC
    class CreateGenesisConsentState(val externalId:String)  : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction for new genesis block.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object CHECKING_UNIQUE_CONSTRAINT : Step("Storing unique constraint in db.")
            object FINALISING_TRANSACTION : Step("Recording transaction in the vault.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    CHECKING_UNIQUE_CONSTRAINT,
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
            val consentState = ConsentState(UniqueIdentifier(externalId), emptySet(), setOf(serviceHub.myInfo.legalIdentities.first()))
            val txCommand = Command(ConsentContract.ConsentCommands.GenesisCommand(), consentState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(consentState, ConsentContract.CONTRACT_ID)
                    .addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = CHECKING_UNIQUE_CONSTRAINT
            store(consentState.consentStateUUID)

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(partSignedTx, emptySet<FlowSession>(), FINALISING_TRANSACTION.childProgressTracker()))
        }

        private fun store(uuid: UniqueIdentifier) : DatabaseTransaction {
            val tx = (serviceHub as AbstractNode<*>.ServiceHubInternalImpl).database.currentOrNew()
            val st = serviceHub.jdbcSession().prepareStatement("INSERT INTO consent_states VALUES(?, ?)")
            st.setString(1, uuid.id.toString())
            st.setString(2, uuid.externalId)
            st.execute()
            return tx
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
    class AddConsent(val consentStateUuid:UniqueIdentifier, val attachments: Set<SecureHash>, val legalEntities: Set<String>, val peers: Set<CordaX500Name>) : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
            object FINDING_PREVIOUS_STATE : Step("Finding previous state to consume (Genesis or existing consent).")
            object GENERATING_TRANSACTION : Step("Generating transaction based on new consent request.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
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

            // names to Party
            val parties = peers.map{ serviceHub.networkMapCache.getPeerByLegalName(it)!! }.toMutableSet()
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

            val currentStateRef = pages.states.first()
            val currentState = currentStateRef.state.data

            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val newState = ConsentBranch(UniqueIdentifier(externalId = consentStateUuid.externalId), consentStateUuid, attachments, legalEntities, emptyList(), parties + serviceHub.myInfo.legalIdentities.first())
            val txCommand = Command(ConsentContract.ConsentCommands.AddCommand(), newState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(newState, ConsentContract.CONTRACT_ID)
                    .addCommand(txCommand)

            // add all attachments to transaction
            attachments.forEach { txBuilder.addAttachment(it) }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            var otherPartySessions = parties.map { initiateFlow(it) }
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    /**
     * Counter party flow for AddConsent
     * All checks are done within the contract. This flow just checks if the right command is used.
     */
    @InitiatedBy(AddConsent::class)
    class AcceptAddConsent(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val command = stx.tx.commands.single().value
                    requireThat {
                        "command is an AddCommand" using (command is ConsentContract.ConsentCommands.AddCommand)
                    }
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }

    }

//    /**
//     * Flow for accepting a ConsentRequestState
//     *
//     * @param externalId the HMAC (using custodian SK) of the patient record, must be same as the new consent request state
//     * @param approvedSigs new signatures of involved parties
//     */
//    @InitiatingFlow
//    @StartableByRPC
//    class AcceptConsentRequest(val consentStateUUID:UniqueIdentifier, val approvedSigs: List<AttachmentSignature>) : FlowLogic<SignedTransaction>() {
//
//        /**
//         * Define steps
//         */
//        companion object {
//            object FIND_CURRENT_STATE : Step("Finding existing ConsentRequestState record.")
//            object GENERATING_TRANSACTION : Step("Generating transaction based on existing consent request.")
//            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
//            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
//            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
//                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
//            }
//
//            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
//                override fun childProgressTracker() = FinalityFlow.tracker()
//            }
//
//            fun tracker() = ProgressTracker(
//                    FIND_CURRENT_STATE,
//                    GENERATING_TRANSACTION,
//                    VERIFYING_TRANSACTION,
//                    SIGNING_TRANSACTION,
//                    GATHERING_SIGS,
//                    FINALISING_TRANSACTION
//            )
//        }
//
//        override val progressTracker = tracker()
//
//        @Suspendable
//        override fun call(): SignedTransaction {
//            // Obtain a reference to the notary we want to use.
//            val notary = serviceHub.networkMapCache.notaryIdentities[0]
//
//            // identity of this node
//            val me = serviceHub.myInfo.legalIdentities.first()
//
//            // Stage 0.
//            progressTracker.currentStep = FIND_CURRENT_STATE
//            val criteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(me),
//                    linearId = listOf(consentStateUUID),
//                    status = Vault.StateStatus.UNCONSUMED,
//                    contractStateTypes = setOf(nl.nuts.consent.state.ConsentBranch::class.java))
//
//            val pages: Vault.Page<ConsentBranch> = serviceHub.vaultService.queryBy(criteria = criteria)
//
//            if (pages.states.size != 1) {
//                throw FlowException("Given external ID does not have any unconsumed states")
//            }
//
//            val currentStateRef = pages.states.first()
//            val currentState = currentStateRef.state.data
//            val newState = currentState.copy(signatures = currentState.signatures + approvedSigs)
//
//            // Stage 1.
//            progressTracker.currentStep = GENERATING_TRANSACTION
//            // Generate an unsigned transaction.
//            val txCommand = Command(ConsentContract.ConsentCommands.AcceptCommand(), newState.participants.map { it.owningKey })
//            val txBuilder = TransactionBuilder(notary)
//                    .addInputState(currentStateRef)
//                    .addOutputState(newState, ConsentContract.CONTRACT_ID)
//                    .addCommand(txCommand)
//
//            // add all previous attachments to transaction
//            currentState.attachments.forEach { txBuilder.addAttachment(it) }
//
//            progressTracker.currentStep = VERIFYING_TRANSACTION
//            // Verify that the transaction is valid.
//            txBuilder.verify(serviceHub)
//
//            progressTracker.currentStep = SIGNING_TRANSACTION
//            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
//
//            progressTracker.currentStep = GATHERING_SIGS
//            var otherPartySessions = (newState.participants - me).map { initiateFlow(it) }
//            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))
//
//            progressTracker.currentStep = FINALISING_TRANSACTION
//            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
//        }
//    }
//
//    /**
//     * Counter party flow for AcceptConsentRequest
//     * All checks are done within the contract. This flow just checks if the right state is created.
//     * todo: naming
//     */
//    @InitiatedBy(AcceptConsentRequest::class)
//    class AcceptAcceptConsentRequest(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
//        @Suspendable
//        override fun call(): SignedTransaction {
//            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
//                override fun checkTransaction(stx: SignedTransaction) = requireThat {
//                    val output = stx.tx.outputs.single().data
//                    "This must be a ConsentRequest transaction." using (output is ConsentBranch)
//                    // contract is also executed on this transaction
//                }
//            }
//            val txId = subFlow(signTransactionFlow).id
//
//            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
//        }
//
//    }
//
//    /**
//     * Flow for finalizing a ConsentRequestState, creating a ConsentState
//     *
//     * @param consentStateUUID the unique identifier used in the ConsentRequestState
//     */
//    @InitiatingFlow
//    @StartableByRPC
//    class FinalizeConsentRequest(val consentStateUUID:UniqueIdentifier) : FlowLogic<SignedTransaction>() {
//
//        /**
//         * Define steps
//         */
//        companion object {
//            object FIND_CURRENT_STATE : Step("Finding existing ConsentRequestState record.")
//            object GENERATING_TRANSACTION : Step("Generating transaction based on existing consent request.")
//            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
//            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
//            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
//                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
//            }
//
//            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
//                override fun childProgressTracker() = FinalityFlow.tracker()
//            }
//
//            fun tracker() = ProgressTracker(
//                    FIND_CURRENT_STATE,
//                    GENERATING_TRANSACTION,
//                    VERIFYING_TRANSACTION,
//                    SIGNING_TRANSACTION,
//                    GATHERING_SIGS,
//                    FINALISING_TRANSACTION
//            )
//        }
//
//        override val progressTracker = tracker()
//
//        @Suspendable
//        override fun call(): SignedTransaction {
//            // Obtain a reference to the notary we want to use.
//            val notary = serviceHub.networkMapCache.notaryIdentities[0]
//
//            // identity of this node
//            val me = serviceHub.myInfo.legalIdentities.first()
//
//            // Stage 0.
//            progressTracker.currentStep = FIND_CURRENT_STATE
//            val criteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(me),
//                    linearId = listOf(consentStateUUID),
//                    status = Vault.StateStatus.UNCONSUMED,
//                    contractStateTypes = setOf(nl.nuts.consent.state.ConsentBranch::class.java))
//
//            val pages: Vault.Page<ConsentBranch> = serviceHub.vaultService.queryBy(criteria = criteria)
//
//            if (pages.states.size != 1) {
//                throw FlowException("Given external ID does not have the correct amount of unconsumed states")
//            }
//
//            val currentStateRef = pages.states.first()
//            val currentState = currentStateRef.state.data
//            val newState = ConsentState(consentStateUUID = currentState.consentStateUUID,
//                    attachments = currentState.attachments, parties = currentState.parties)
//
//            // Stage 1.
//            progressTracker.currentStep = GENERATING_TRANSACTION
//            // Generate an unsigned transaction.
//            val txCommand = Command(ConsentContract.ConsentCommands.FinalizeCommand(), newState.participants.map { it.owningKey })
//            val txBuilder = TransactionBuilder(notary)
//                    .addInputState(currentStateRef)
//                    .addOutputState(newState, ConsentContract.CONTRACT_ID)
//                    .addCommand(txCommand)
//
//            // add all previous attachments to transaction
//            currentState.attachments.forEach { txBuilder.addAttachment(it) }
//
//            progressTracker.currentStep = VERIFYING_TRANSACTION
//            // Verify that the transaction is valid.
//            txBuilder.verify(serviceHub)
//
//            progressTracker.currentStep = SIGNING_TRANSACTION
//            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
//
//            progressTracker.currentStep = GATHERING_SIGS
//            var otherPartySessions = (currentState.participants - me).map { initiateFlow(it) }
//
//            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))
//
//            // uniqueness
//            store(currentState.consentStateUUID.externalId!!)
//
//            progressTracker.currentStep = FINALISING_TRANSACTION
//            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
//        }
//
//        private fun store(externalId: String) : DatabaseTransaction {
//            val tx = (serviceHub as AbstractNode<*>.ServiceHubInternalImpl).database.currentOrNew()
//            val st = serviceHub.jdbcSession().prepareStatement("INSERT INTO consent_states VALUES(?)")
//            st.setString(1, externalId)
//            st.execute()
//            return tx
//        }
//    }
//
//    /**
//     * Counter party flow for FinalizeConsentRequest
//     * All checks are done within the contract. This flow just checks if the right state is created.
//     */
//    @InitiatedBy(FinalizeConsentRequest::class)
//    class AcceptFinalizeConsentRequest(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
//        @Suspendable
//        override fun call(): SignedTransaction {
//            var consentState: ConsentState? = null
//
//            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
//                override fun checkTransaction(stx: SignedTransaction) = requireThat {
//                    val output = stx.tx.outputs.single().data
//                    "This must be a Consent transaction." using (output is ConsentState)
//                    // contract is also executed on this transaction
//                    consentState = output as ConsentState
//                }
//            }
//            val txId = subFlow(signTransactionFlow).id
//
//            store(consentState!!.consentStateUUID.externalId!!)
//
//            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
//        }
//
//        private fun store(externalId: String) {
//            (serviceHub as AbstractNode<*>.ServiceHubInternalImpl).database.currentOrNew()
//            val st = serviceHub.jdbcSession().prepareStatement("INSERT INTO consent_states VALUES(?)")
//            st.setString(1, externalId)
//            st.execute()
//        }
//    }
}
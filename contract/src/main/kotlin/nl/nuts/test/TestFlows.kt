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

package nl.nuts.test

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

object TestFlows {

    @InitiatingFlow
    @StartableByRPC
    class IssueGenesis(val externalId:String) : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
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

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val state = GenesisState(UniqueIdentifier(externalId), setOf(serviceHub.myInfo.legalIdentities.first()))
            val txCommand = Command(TestContract.TestCommands.GenesisCommand(), state.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(state, TestContract.CONTRACT_ID)
                    .addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // TODO, store in db via jdbc (uuid + externalId)

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(partSignedTx, emptySet<FlowSession>(), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class BranchFlow(val uuid:UniqueIdentifier, val peers: Set<CordaX500Name>) : FlowLogic<SignedTransaction>() {

        companion object {
            object FIND_CURRENT_STATE : Step("Finding existing ConsentRequestState record.")
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

            // names to Party
            val parties = peers.map{ serviceHub.networkMapCache.getPeerByLegalName(it)!! }.toMutableSet()
            parties.remove(serviceHub.myInfo.legalIdentities.first())

            val newState = AddState(uuid = UniqueIdentifier(uuid.externalId), main = uuid, parties = parties + me)

            progressTracker.currentStep = FIND_CURRENT_STATE
            val criteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(me),
                    linearId = listOf(uuid),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(GenesisState::class.java))
            val pages: Vault.Page<GenesisState> = serviceHub.vaultService.queryBy(criteria = criteria)

            if (pages.states.size != 1) {
                throw FlowException("Given external ID does not have any unconsumed states")
            }

            val currentStateRef = pages.states.first()
            val currentState = currentStateRef.state.data
            val nextState = currentState.copy()

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(TestContract.TestCommands.BranchCommand(), newState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(currentStateRef)
                    .addOutputState(newState, TestContract.CONTRACT_ID)
                    .addOutputState(nextState, TestContract.CONTRACT_ID)
                    .addCommand(txCommand)

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

    @InitiatedBy(BranchFlow::class)
    class AcceptBranchFlow(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
//                    val output = stx.tx.outputs.single().data
//                    "This must be a ConsentRequest transaction." using (output is ConsentRequestState)
                    // contract is also executed on this transaction
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }

    }

    @InitiatingFlow
    @StartableByRPC
    class MergeFlow(val uuid:UniqueIdentifier, val peers: Set<CordaX500Name>) : FlowLogic<SignedTransaction>() {

        /**
         * Define steps
         */
        companion object {
            object FIND_CURRENT_STATE : Step("Finding existing ConsentRequestState record.")
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

            // names to Party
            val parties = peers.map{ serviceHub.networkMapCache.getPeerByLegalName(it)!! }.toMutableSet()
            parties.remove(serviceHub.myInfo.legalIdentities.first())

            // branch

            progressTracker.currentStep = FIND_CURRENT_STATE
            val criteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(me),
                    linearId = listOf(uuid),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(AddState::class.java))
            val pages: Vault.Page<AddState> = serviceHub.vaultService.queryBy(criteria = criteria)
            if (pages.states.size != 1) {
                throw FlowException("Given uuid does not have any unconsumed states")
            }

            val addStateRef = pages.states.first()
            val addState = addStateRef.state.data

            // main
            val cr = QueryCriteria.LinearStateQueryCriteria(participants = listOf(me),
                    linearId = listOf(addState.main),
                    status = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes = setOf(GenesisState::class.java))
            val ps: Vault.Page<GenesisState> = serviceHub.vaultService.queryBy(criteria = cr)
            if (ps.states.size != 1) {
                throw FlowException("Given uuid does not have any unconsumed states")
            }

            val genesisStateRef = ps.states.first()
            val genesisState = genesisStateRef.state.data

            // history
            // serviceHub.validatedTransactions.getTransaction(genesisStateRef.ref.txhash).inputs

            val nextState = ActiveState(uuid = genesisState.uuid, parties = addState.parties + genesisState.parties)

            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val txCommand = Command(TestContract.TestCommands.MergeCommand(), nextState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(addStateRef)
                    .addInputState(genesisStateRef)
                    .addOutputState(nextState, TestContract.CONTRACT_ID)
                    .addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            var otherPartySessions = (addState.participants - me).map { initiateFlow(it) }
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions, GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, otherPartySessions, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(MergeFlow::class)
    class AcceptMergeFlow(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    //                    val output = stx.tx.outputs.single().data
//                    "This must be a ConsentRequest transaction." using (output is ConsentRequestState)
                    // contract is also executed on this transaction
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }

    }
}
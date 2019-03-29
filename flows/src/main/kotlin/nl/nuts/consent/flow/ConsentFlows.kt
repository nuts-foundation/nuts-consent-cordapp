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
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import nl.nuts.consent.contract.ConsentContract

import nl.nuts.consent.state.ConsentRequestState

/**
 * Collection of ConsentRequest flows: New, Accept, Finalize with their counter parties
 */
object ConsentRequestFlows {

    /**
     * Flow for creating a new ConsentRequestState than can then be accepted by other parties
     *
     * @param externalId the HMAC (using custodian SK) of the patient record
     * @param attachments hashes of attachments containing the consent records
     * @param parties Other nodes involved in transaction (excluding this one)
     */
    @InitiatingFlow
    @StartableByRPC
    class NewConsentRequest(val externalId:String, val attachments: Set<SecureHash>, val parties: List<Party>) : FlowLogic<SignedTransaction>() {

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
            val consentRequestState = ConsentRequestState(externalId, attachments, emptyList(), parties + serviceHub.myInfo.legalIdentities.first())
            val txCommand = Command(ConsentContract.ConsentCommands.CreateRequest(), consentRequestState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(consentRequestState, ConsentContract.CONTRACT_ID)
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
     * Counter party flow for NewConsentRequest
     * All checks are done within the contract. This flow just checks if the right state is created.
     */
    @InitiatedBy(NewConsentRequest::class)
    class AcceptNewConsentRequest(val askingPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(askingPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a ConsentRequest transaction." using (output is ConsentRequestState)
                    // contract is also executed on this transaction
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(askingPartySession, expectedTxId = txId))
        }

    }
}
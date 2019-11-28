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
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.startFlow
import nl.nuts.consent.contract.AttachmentSignature
import nl.nuts.consent.contract.ConsentContract
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.FINALISING_TRANSACTION
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.FINDING_PREVIOUS_STATE
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.GATHERING_SIGS
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.GENERATING_TRANSACTION
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.SENDING_DATA
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.SIGNING_TRANSACTION
import nl.nuts.consent.flow.ConsentFlows.CreateConsentBranch.Companion.VERIFYING_TRANSACTION
import nl.nuts.consent.state.ConsentBranch
import nl.nuts.consent.state.ConsentState
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.fail

class MaliciousSendingFlowTest : GenericFlowTests() {
    /**
     * This test detects duplicates on client side
     */
    @Test
    fun `Branching with already added att raises on initiating side`() {
        val genesisTx = runGenesisTransaction("maliciousConsentTest-1")
        val genesisState = genesisTx.tx.outputStates.first() as ConsentState
        var branchTx = runAddTransaction(genesisState.linearId)
        var branchState = branchTx.tx.outputsOfType<ConsentBranch>().first()
        runSignTransaction(branchState.linearId, validHashAdd1!!)
        runMergeTransaction(branchState.linearId)

        // branch again should raise
        try {
            runAddTransaction(genesisState.linearId, setOf(validHashDup!!))
            fail("Expected flow exception")
        } catch (e:FlowException) {
            assertEquals("ConsentBranch contains 1 or more records already present in ConsentState, detected at origin", e.message)
        }
    }

    @Test
    fun `Branching with already added att raises on accepting side`() {
        val genesisTx = runGenesisTransaction("maliciousConsentTest-2")
        val genesisState = genesisTx.tx.outputStates.first() as ConsentState
        var branchTx = runAddTransaction(genesisState.linearId)
        var branchState = branchTx.tx.outputsOfType<ConsentBranch>().first()
        runSignTransaction(branchState.linearId, validHashAdd1!!)
        runMergeTransaction(branchState.linearId)

        // branch again should raise
        try {
            runMaliciousAddTransaction(genesisState.linearId, setOf(validHashDup!!))
            fail("Expected flow exception")
        } catch (e:FlowException) {
            assertEquals("ConsentBranch contains 1 or more records already present in ConsentState, detected at counter party", e.message)
        }
    }

    private fun runGenesisTransaction(externalId: String): SignedTransaction {
        val flow = ConsentFlows.CreateGenesisConsentState(externalId)
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runAddTransaction(uuid: UniqueIdentifier, hash:Set<SecureHash> = setOf(validHashAdd1!!)): SignedTransaction {
        val flow = ConsentFlows.CreateConsentBranch(UUID.randomUUID(), uuid, hash, setOf("http://nuts.nl/naming/organisation#test"), setOf(b.info.singleIdentity().name))
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runMaliciousAddTransaction(uuid: UniqueIdentifier, hash:Set<SecureHash> = setOf(validHashAdd1!!)): SignedTransaction {
        val flow = MaliciousCreateConsentBranch(UUID.randomUUID(), uuid, hash, setOf("http://nuts.nl/naming/organisation#test"), setOf(b.info.singleIdentity().name))
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runSignTransaction(uuid: UniqueIdentifier, attHash: SecureHash): SignedTransaction {
        val attSig = AttachmentSignature("http://nuts.nl/naming/organisation#test", attHash, b.services.keyManagementService.sign(attHash.bytes, b.info.legalIdentities.first().owningKey))
        val flow = ConsentFlows.SignConsentBranch(uuid, listOf(attSig))
        val future = b.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }

    private fun runMergeTransaction(uuid: UniqueIdentifier): SignedTransaction {
        val flow = ConsentFlows.MergeBranch(uuid)
        val future = a.services.startFlow(flow)
        network.runNetwork()
        return future.resultFuture.getOrThrow()
    }



    /**
     * Same as CreateConsentBranch but it doesn't check for duplicates
     */
    class MaliciousCreateConsentBranch(consentBranchUUID: UUID, consentStateUuid: UniqueIdentifier, attachments: Set<SecureHash>, legalEntities: Set<String>, peers: Set<CordaX500Name>) :
            ConsentFlows.CreateConsentBranch(consentBranchUUID, consentStateUuid, attachments, legalEntities, peers) {
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

            val referencedAttachmentsInBranch = ConsentFlows.referencedAttachments(newState, serviceHub)
            if (referencedAttachmentsInBranch.isNotEmpty()) {
                txBuilder.addCommand(Command(ConsentContract.ConsentCommands.UpdateCommand(), newState.participants.map { it.owningKey }))
            }

            // add all attachments to transaction
            attachments.forEach { txBuilder.addAttachment(it) }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid according to contract.
            txBuilder.verify(serviceHub)

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
    }
}
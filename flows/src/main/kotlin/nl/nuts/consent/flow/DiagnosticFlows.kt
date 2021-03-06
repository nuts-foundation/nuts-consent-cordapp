/*
 *     Nuts consent cordapp
 *     Copyright (C) 2020 Nuts community
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
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.randomOrNull
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Collection of flows for checking network health
 */
object DiagnosticFlows {

    @Suspendable
    fun checkReceivedData(counterPartySession: FlowSession, dataToReceive: String) {
        val packet1= counterPartySession.receive<String>()
        val received: String = packet1.unwrap {
            it
        }

        if (dataToReceive != received) {
            throw FlowException("Flow returned unknown data: $received")
        }
    }

    @InitiatingFlow
    @StartableByRPC
    abstract class PingFlow : FlowLogic<Unit>() {
        val localLogger: Logger = LoggerFactory.getLogger(this::class.java)

        /**
         * Define steps
         */
        companion object {
            object FINDING_PEER : ProgressTracker.Step("Find address of peer.")
            object SEND_DATA : ProgressTracker.Step("Sending ping.")
            object RECEIVING_DATA : ProgressTracker.Step("Waiting for pong.")

            fun tracker() = ProgressTracker(
                    FINDING_PEER,
                    SEND_DATA,
                    RECEIVING_DATA
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        abstract fun findTarget() : Party

        @Suspendable
        override fun call() {
            progressTracker.currentStep = FINDING_PEER

            val p = findTarget()
            localLogger.debug("Sending ping to ${p.name}")

            progressTracker.currentStep = SEND_DATA

            val counterPartySession: FlowSession = initiateFlow(p)
            counterPartySession.send("ping")

            progressTracker.currentStep = RECEIVING_DATA

            checkReceivedData(counterPartySession, "pong")
        }
    }

    /**
     * A ping flow targeted at the first Notary
     */
    @StartableByRPC
    class PingNotaryFlow : PingFlow() {
        @Suspendable
        override fun findTarget(): Party {
            try {
                return serviceHub.networkMapCache.notaryIdentities.first()
            } catch (e: NoSuchElementException) {
                throw FlowException(e)
            }
        }
    }

    /**
     * A ping flow targeted at a random node
     */
    @StartableByRPC
    class PingRandomFlow : PingFlow() {
        @Suspendable
        override fun findTarget(): Party {
            val me = serviceHub.myInfo.legalIdentities

            return serviceHub.networkMapCache.allNodes
                    .map { it.legalIdentities }
                    .toList()
                    .flatten()
                    .filter { serviceHub.networkMapCache.getNotary(it.name) == null } // no notaries
                    .filterNot { me.contains(it) } // not myself
                    .randomOrNull() ?: throw FlowException("No nodes available")
        }
    }

    @InitiatedBy(PingFlow::class)
    class PongFlow(val counterPartySession: FlowSession) : FlowLogic<Unit>() {
        val localLogger: Logger = LoggerFactory.getLogger(this::class.java)

        companion object {
            object RECEIVING_DATA : ProgressTracker.Step("Waiting for ping.")
            object SEND_DATA : ProgressTracker.Step("Sending pong.")

            fun tracker() = ProgressTracker(
                    RECEIVING_DATA,
                    SEND_DATA
            )
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call() {
            progressTracker.currentStep = RECEIVING_DATA

            checkReceivedData(counterPartySession, "ping")
            localLogger.debug("Received ping from ${counterPartySession.counterparty.name}")

            progressTracker.currentStep = SEND_DATA

            counterPartySession.send("pong")
        }
    }
}
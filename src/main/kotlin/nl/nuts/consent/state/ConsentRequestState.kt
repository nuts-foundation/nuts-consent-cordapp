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

package nl.nuts.consent.state

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * The ConsentRequestState represents the current state of the request. This extra state is required
 * for doing an external check by a Party. The external check can be a long running transaction.
 *
 * @param consentStateExternalId the external id of the ConsentState record
 * @param parties involved parties
 */
data class ConsentRequestState(val consentStateExternalId: String,
                               val parties: List<Party> = ArrayList()) : LinearState {

    override val linearId: UniqueIdentifier get() = UniqueIdentifier("${consentStateExternalId}_REQ")
    override val participants: List<AbstractParty> get() = parties

    override fun toString() = linearId.toString()
}
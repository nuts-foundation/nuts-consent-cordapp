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

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import nl.nuts.consent.contract.ConsentContract
import nl.nuts.consent.contract.AttachmentSignature

/**
 * The ConsentRequestState represents the current state of the request. This extra state is required
 * for doing an external check by a Party. The external check can be a long running transaction.
 *
 * @param consentStateExternalId the external id of the ConsentState record
 * @param attachments list of attachment hashes that need to be present at each transaction
 * @param signatures list of Party signatures representing parties that have completed checks against the encrypted attachment
 * @param legalEntities list of legal entities mentioned in the consent resource and must be involved in signing
 * @param parties involved parties
 */
@BelongsToContract(ConsentContract::class)
data class ConsentRequestState(val consentStateUUID: UniqueIdentifier,
                               val attachments: Set<SecureHash>,
                               val legalEntities: List<String>,
                               val signatures: List<AttachmentSignature>,
                               val parties: List<Party> = ArrayList()) : LinearState {

    constructor(externalId: String,
                attachments: Set<SecureHash>,
                legalEntities: List<String>,
                signatures: List<AttachmentSignature>,
                parties: List<Party>) : this(UniqueIdentifier(externalId), attachments, legalEntities, signatures, parties)

    override val linearId: UniqueIdentifier get() = consentStateUUID
    override val participants: List<Party> get() = parties

    override fun toString() = linearId.toString()
}
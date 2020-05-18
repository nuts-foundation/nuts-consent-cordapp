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
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import nl.nuts.consent.contract.ConsentContract
import nl.nuts.consent.contract.AttachmentSignature
import java.time.OffsetDateTime

/**
 * The ConsentRequestState represents the current state of the request. This extra state is required
 * for doing an external check by a Party. The external check can be a long running transaction.
 *
 * @param uuid the uuid of the ConsentState record
 * @param branchPoint the consent record from which this state branched
 * @param attachments list of attachment hashes that need to be present at each transaction
 * @param signatures list of Party signatures representing parties that have completed checks against the encrypted attachment
 * @param legalEntities list of legal entities mentioned in the consent resource and must be involved in signing
 * @param parties involved parties
 * @param state current state of the branch, used for providing feedback before consuming in case of an error
 * @param initiatingNode the node identity that started the transaction
 * @param initiatingLegalEntity the legalEntity that started the transaction, usually the custodian
 * @param branchTime the datetime this branch was created of the ConsentState
 * @param stateTime the datetime this particular BranchState was created
 * @param closingReason reason for non-success state, computer generated, should include vendor and custodian information
 * @param closingComment user comment on non-success state
 */
@BelongsToContract(ConsentContract::class)
class ConsentBranch : ConsentBase {

    val branchPoint : UniqueIdentifier
    val legalEntities: Set<String>
    val signatures: List<AttachmentSignature>
    val state: BranchState
    val initiatingNode: String
    val initiatingLegalEntity: String
    val branchTime: OffsetDateTime
    val stateTime: OffsetDateTime
    val closingReason: String?
    val closingComment: String?

    constructor(uuid: UniqueIdentifier,
                branchPoint: UniqueIdentifier,
                attachments: Set<SecureHash>,
                legalEntities: Set<String>,
                signatures: List<AttachmentSignature>,
                parties: Set<Party> = emptySet(),
                state: BranchState = BranchState.Open,
                initiatingNode: String = "",
                initiatingLegalEntity: String = "",
                branchTime: OffsetDateTime = OffsetDateTime.now(),
                stateTime: OffsetDateTime = OffsetDateTime.now(),
                closingReason: String? = null,
                closingComment: String? = null
    ) : super(uuid, attachments, parties) {
        this.branchPoint = branchPoint
        this.legalEntities = legalEntities
        this.signatures = signatures
        this.state = state
        this.initiatingNode = initiatingNode
        this.initiatingLegalEntity = initiatingLegalEntity
        this.branchTime = branchTime
        this.stateTime = stateTime
        this.closingReason = closingReason
        this.closingComment = closingComment
    }

    fun copy(uuid: UniqueIdentifier = this.uuid,
             branchPoint: UniqueIdentifier = this.branchPoint,
             attachments: Set<SecureHash> = this.attachments,
             legalEntities: Set<String> = this.legalEntities,
             signatures: List<AttachmentSignature> = this.signatures,
             parties: Set<Party> = this.parties,
             state:BranchState = this.state,
             initiatingNode: String = this.initiatingNode,
             initiatingLegalEntity: String = this.initiatingLegalEntity,
             branchTime: OffsetDateTime = this.branchTime,
             stateTime: OffsetDateTime = this.stateTime,
             closingReason: String? = this.closingReason,
             closingComment: String? = this.closingComment
    ) : ConsentBranch {
        return ConsentBranch(
            uuid,
            branchPoint,
            attachments,
            legalEntities,
            signatures,
            parties,
            state,
            initiatingNode,
            initiatingLegalEntity,
            branchTime,
            stateTime,
            closingReason,
            closingComment
        )
    }
}
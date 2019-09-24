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

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import nl.nuts.consent.contract.ConsentContract

/**
 * Common logic for both ConsentState and ConsentBranch
 */
abstract class ConsentBase(protected val uuid: UniqueIdentifier,
                           val attachments: Set<SecureHash> = emptySet(),
                           val parties: Set<Party> = emptySet()) : LinearState {
    override val linearId: UniqueIdentifier get() = uuid
    override val participants: List<Party> get() = parties.toList()

    override fun toString() = linearId.toString()

    @Synchronized
    fun referencedAttachments(attachmentsMap : Map<SecureHash, Attachment>) : Set<SecureHash> {

        val referencedAttachments = mutableSetOf<SecureHash>()

        attachments.forEach {
            val att = attachmentsMap[it]

            // non existing attachments are triggered by other requirements
            if (att != null) {
                val metadata = ConsentContract.extractMetadata(att)
                if (metadata.previousAttachmentId != null) {
                    referencedAttachments.add(SecureHash.parse(metadata.previousAttachmentId))
                }
            }
        }

        return referencedAttachments
    }
}
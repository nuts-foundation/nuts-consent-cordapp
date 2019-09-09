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
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import nl.nuts.consent.contract.ConsentContract
import nl.nuts.consent.schema.ConsentSchemaV1

/**
 * State representing the actual consent records. All data is stored as attachments.
 * The externalId is created by the initiator and must be reproducible.
 *
 * @param uuid UUID + unique id generated by custodian
 * @param attachments all active consent records as attachment
 * @param parties all involved parties
 */
@CordaSerializable
@BelongsToContract(ConsentContract::class)
data class ConsentState(
        val uuid: UniqueIdentifier,
        val version: Int,
        val attachments: Set<SecureHash> = emptySet(),
        val parties: Set<Party> = emptySet()) : LinearState, QueryableState {

    override val linearId: UniqueIdentifier get() = uuid
    override val participants: List<Party> get() = parties.toList()

    override fun toString() = "${uuid.toString()}_${version}"

    override fun generateMappedObject(schema: MappedSchema): PersistentState = ConsentSchemaV1.PersistentConsent(uuid, version)

    override fun supportedSchemas(): Iterable<MappedSchema> =  listOf(ConsentSchemaV1)
}
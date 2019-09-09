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

package nl.nuts.consent.schema

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

object ConsentSchema

object ConsentSchemaV1 : MappedSchema(
        schemaFamily = ConsentSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentConsent::class.java)) {

    /**
     * This Entity is only defined so Corda picks it up and creates the table for us.
     */
    @Entity
    @Table(name = "consent_states")
    class PersistentConsent(

            @Id
            @Column(name = "external_id")
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            var uuid: String

    ) {
        constructor(uid: UniqueIdentifier)
                : this(externalId = uid.externalId,
                uuid = uid.id.toString())

        constructor() : this(UniqueIdentifier())
    }
}
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

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

object ConsentSchema

object ConsentSchemaV1 : MappedSchema(
        schemaFamily = ConsentSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentConsent::class.java)) {

    @Entity
    @Table(name = "consent_states")
    class PersistentConsent(
            /**
             * Acts as id and reference to consent state
             */
            @Id
            @Column(name = "uuid", unique = true)
            var uuid: String,

            /**
             * Acts as unique index on custodian/subject/actor triples
             */
            @Column(name = "external_id", unique = true)
            var externalId: String
    ) {
        constructor() : this("", "")
    }
}
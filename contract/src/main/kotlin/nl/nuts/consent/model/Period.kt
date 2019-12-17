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

package nl.nuts.consent.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.contracts.requireThat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.validation.constraints.NotNull

data class Period (
    @get:NotNull
    @JsonProperty("validFrom")
    val validFrom: OffsetDateTime,

    @JsonProperty("validTo")
    val validTo: OffsetDateTime? = null
) {
    fun verify() {
        requireThat {
            "validTo comes after valid From" using (validTo == null || validTo.isAfter(validFrom))
        }
    }
}
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

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.core.contracts.requireThat
import javax.validation.constraints.*

/**
 * json format for metadata.json in attachment. Used as json schema
 */
data class ConsentMetadata (
    @get:NotNull
    @JsonProperty("domain") val domain:List<Domain>,

    @get:NotNull
    @JsonProperty("secureKey") val secureKey: SymmetricKey,

    @get:NotNull
    @JsonProperty("organisationSecureKeys") val organisationSecureKeys: List<ASymmetricKey>,

    @JsonProperty("previousAttachmentId") val previousAttachmentId: String? = null,

    @get:NotNull
    @JsonProperty("period") val period: Period,

    /**
     * The SHA256 of the unencrypted consent record. Used to detect duplicates inside a Corda transaction
     */
    @get:NotNull
    @JsonProperty("consentRecordHash") val consentRecordHash: String
) {
    fun verify() {
        requireThat {
            "the list of Domains is not empty" using !domain.isEmpty()
            "the list of organisationSecureKeys is not empty" using !organisationSecureKeys.isEmpty()
        }
        period.verify()
    }
}
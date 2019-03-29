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

package nl.nuts.consent.contract

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.SignatureException

/**
 * Wrapper for digital signature created by a Party validating that the content of the attachment is indeed intended for that Party.
 */
@CordaSerializable
data class PartyAttachmentSignature(val party: Party, val attachmentHash: SecureHash, val signature: DigitalSignature.WithKey) {
    fun verify() : Boolean {
        try {
            return signature.verify(attachmentHash.bytes)
        } catch (e: SignatureException) {
            return false
        }
    }
}
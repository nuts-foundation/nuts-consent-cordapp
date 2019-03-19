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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.junit.BeforeClass
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import kotlin.test.assertEquals

class ConsentMetadataTest {
    companion object {
        val objectMapper = ObjectMapper()

        @BeforeClass @JvmStatic fun setup() {
            objectMapper.registerModule(JavaTimeModule())
            objectMapper.dateFormat = SimpleDateFormat.getDateInstance()
        }
    }

    @Test
    fun `ConsentMetadata period serialises correctly`() {
        val m1 = sdm()

        assertEquals(LocalDate.now(), m1.period!!.validFrom)
        assertEquals(LocalDate.now().plusDays(1), m1.period!!.validTo)
    }

    @Test
    fun `ConsentMetadata domain serialises correctly`() {
        assertEquals(Domain.MEDICAL, sdm().domain!!.first())
    }

    @Test
    fun `ConsentMetadata previousAttachmentId serialises correctly`() {
        assertEquals("uuid", sdm().previousAttachmentId)
    }

    @Test
    fun `ConsentMetadata symmetric key serialises correctly`() {
        val m1 = sdm()

        assertEquals("AES_GCM_256", m1.secureKey!!.alg)
        assertEquals("567898==", m1.secureKey!!.iv)
    }

    @Test
    fun `ConsentMetadata asymmetric keys serialises correctly`() {
        val m1 = sdm()

        assertEquals("RSA_3k", m1.organisationSecureKeys!!.first().alg)
        assertEquals("123456==", m1.organisationSecureKeys!!.first().cipherText)
        assertEquals("http://nuts.nl/naming/organisation#test", m1.organisationSecureKeys!!.first().organisationId)

    }

    // helper for standard serialize/deserialize
    private fun sdm() : ConsentMetadata {
        return objectMapper.readValue<ConsentMetadata>(objectMapper.writeValueAsString(createMetadata()), ConsentMetadata::class.java)
    }


    private fun createMetadata() : ConsentMetadata {
        val m1 = ConsentMetadata()

        // period
        val period = Period()
        m1.period = period
        period.validFrom = LocalDate.now()
        period.validTo = LocalDate.now().plusDays(1)

        // domain
        m1.domain = listOf(Domain.MEDICAL)

        // symmetric key
        val secureKey = SymmetricKey()
        secureKey.alg = "AES_GCM_256"
        secureKey.iv = "567898=="
        m1.secureKey = secureKey

        // previousAttachmentId
        m1.previousAttachmentId = "uuid"

        // asymmetric keys
        val assKey = ASymmetricKey()
        assKey.alg = "RSA_3k"
        assKey.cipherText = "123456=="
        assKey.organisationId = "http://nuts.nl/naming/organisation#test"
        m1.organisationSecureKeys = listOf(assKey)

        return m1
    }
}
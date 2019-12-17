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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ConsentMetadataTest {
    companion object {
        val objectMapper = ObjectMapper()

        @BeforeClass @JvmStatic fun setup() {
            objectMapper.findAndRegisterModules()
            objectMapper.dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        }
    }

    @Test
    fun `ConsentMetadata period serialises correctly`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val m1 = sdm(now)

        assertEquals(now, m1.period.validFrom)
        assertEquals(now.plusDays(1), m1.period.validTo)
    }

    @Test
    fun `ConsentMetadata domain serialises correctly`() {
        assertEquals(Domain.medical, sdm().domain.first())
    }

    @Test
    fun `ConsentMetadata previousAttachmentId serialises correctly`() {
        assertEquals("uuid", sdm().previousAttachmentId)
    }

    @Test
    fun `ConsentMetadata symmetric key serialises correctly`() {
        val m1 = sdm()

        assertEquals("AES_GCM_256", m1.secureKey.alg)
        assertEquals("567898==", m1.secureKey.iv)
    }

    @Test
    fun `ConsentMetadata asymmetric keys serialises correctly`() {
        val m1 = sdm()

        assertEquals("RSA_3k", m1.organisationSecureKeys.first().alg)
        assertEquals("123456==", m1.organisationSecureKeys.first().cipherText)
        assertEquals("http://nuts.nl/naming/organisation#test", m1.organisationSecureKeys.first().legalEntity)

    }

    @Test(expected = Test.None::class)
    fun `verify does not raise for valid ConsentMetadata`() {
        val m1 = createMetadata(OffsetDateTime.now())

        m1.verify()
    }

    @Test
    fun `verify raises for empty domain list`() {
        // period
        val period = Period(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1))

        // symmetric key
        val secureKey = SymmetricKey("AES_GCM_256", "567898==")

        // asymmetric keys
        val assKey = ASymmetricKey("http://nuts.nl/naming/organisation#test", "RSA_3k", "123456==")

        val m1 = ConsentMetadata(emptyList(), secureKey, listOf(assKey), "uuid", period, "")

        assertFailsWith(IllegalArgumentException::class) {
            m1.verify()
        }
    }

    @Test
    fun `verify raises for empty organisation secure keys`() {
        // period
        val period = Period(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1))

        // symmetric key
        val secureKey = SymmetricKey("AES_GCM_256", "567898==")

        val m1 = ConsentMetadata(listOf(Domain.medical), secureKey, emptyList(), "uuid", period, "")

        assertFailsWith(IllegalArgumentException::class) {
            m1.verify()
        }
    }

    @Test
    fun `verify raises for invalid period`() {
        // period
        val period = Period(OffsetDateTime.now(), OffsetDateTime.now().minusDays(1))

        // symmetric key
        val secureKey = SymmetricKey("AES_GCM_256", "567898==")

        // asymmetric keys
        val assKey = ASymmetricKey("http://nuts.nl/naming/organisation#test", "RSA_3k", "123456==")

        val m1 = ConsentMetadata(listOf(Domain.medical), secureKey, listOf(assKey), "uuid", period, "")

        assertFailsWith(IllegalArgumentException::class) {
            m1.verify()
        }
    }


    // helper for standard serialize/deserialize
    private fun sdm() : ConsentMetadata {
        return sdm(OffsetDateTime.now())
    }

    private fun sdm(now : OffsetDateTime) : ConsentMetadata {
        val s = objectMapper.writeValueAsString(createMetadata(now))
        return objectMapper.readValue<ConsentMetadata>(s, ConsentMetadata::class.java)
    }


    private fun createMetadata(now : OffsetDateTime) : ConsentMetadata {
        // period
        val period = Period(now, now.plusDays(1))

        // symmetric key
        val secureKey = SymmetricKey("AES_GCM_256", "567898==")

        // asymmetric keys
        val assKey = ASymmetricKey("http://nuts.nl/naming/organisation#test", "RSA_3k", "123456==")

        return ConsentMetadata(listOf(Domain.medical), secureKey, listOf(assKey), "uuid", period, "")
    }
}
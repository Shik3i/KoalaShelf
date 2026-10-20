package net.koalastuff.koalashelf.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AbsFilterAndTokenUtilsTest {

    @Test
    fun `normalizeBaseUrl trims whitespace and strips trailing slashes`() {
        assertEquals("https://abs.example.com", normalizeBaseUrl("  https://abs.example.com/  "))
        assertEquals("http://192.168.1.10:13378", normalizeBaseUrl("http://192.168.1.10:13378///"))
        assertEquals("https://books.koala.net/abs", normalizeBaseUrl("https://books.koala.net/abs/"))
    }

    @Test
    fun `serverLabel extracts clean host and port`() {
        assertEquals("abs.example.com", serverLabel("https://abs.example.com/"))
        assertEquals("192.168.1.10:13378", serverLabel("http://192.168.1.10:13378"))
    }

    @Test
    fun `isVersionAtLeast compares semantic versions accurately`() {
        assertTrue(isVersionAtLeast("2.26.0", "2.26.0"))
        assertTrue(isVersionAtLeast("2.27.1", "2.26.0"))
        assertTrue(isVersionAtLeast("v3.0.0-beta.1", "2.26.0"))
        assertFalse(isVersionAtLeast("2.25.9", "2.26.0"))
    }

    @Test
    fun `tokenExpiry decodes JWT exp claim into seconds`() {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"u1","exp":1800000000}""".toByteArray())
        val jwt = "$header.$payload.sig"

        assertEquals(1800000000L, tokenExpiry(jwt))
        assertNull(tokenExpiry("legacy-opaque-token"))
    }

    @Test
    fun `encodeFilter encodes UTF8 base64 and URL-escapes value`() {
        val encoded = encodeFilter(FilterGroup.GENRES, "Science Fiction")
        assertTrue(encoded.startsWith("genres."))
        assertTrue(encoded.length > "genres.".length)
    }
}

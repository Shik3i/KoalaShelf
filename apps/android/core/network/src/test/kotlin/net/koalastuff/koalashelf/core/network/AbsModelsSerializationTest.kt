package net.koalastuff.koalashelf.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsModelsSerializationTest {

    @Test
    fun testFlexibleGenresAndAuthors() {
        val json = """
            {
              "id": "book-1",
              "libraryId": "lib-1",
              "media": {
                "coverPath": "/metadata/items/book-1/cover.jpg",
                "metadata": {
                  "title": "Anthologie 2024",
                  "genres": "Kurzgeschichte",
                  "authors": ["Anthologie"],
                  "narrators": "Ein Sprecher"
                },
                "tags": "Retro"
              }
            }
        """.trimIndent()

        val item = AbsJson.decodeFromString<LibraryItem>(json)
        assertNotNull(item)
        assertEquals("Anthologie 2024", item.title)
        assertEquals(listOf("Kurzgeschichte"), item.media.metadata.genres)
        assertEquals("Anthologie", item.author)
        assertEquals(listOf("Ein Sprecher"), item.media.metadata.narrators)
        assertEquals(listOf("Retro"), item.media.tags)
        assertTrue(item.hasCover)
    }

    @Test
    fun testSparsePayloadsAndHasCoverFallback() {
        val emptyItem = AbsJson.decodeFromString<LibraryItem>("{}")
        assertEquals("", emptyItem.id)
        assertEquals("", emptyItem.title)
        assertEquals("", emptyItem.author)
        assertFalse(emptyItem.hasCover)

        val nullCoverItem = AbsJson.decodeFromString<LibraryItem>(
            """{"id":"b2","media":{"coverPath":null,"metadata":{"title":null}}}""",
        )
        assertEquals("b2", nullCoverItem.id)
        assertEquals("", nullCoverItem.title)
        assertFalse(nullCoverItem.hasCover)

        val sparseLogin = AbsJson.decodeFromString<LoginResponse>("{}")
        assertEquals("", sparseLogin.user.id)
        assertEquals("", sparseLogin.user.username)
        assertEquals("user", sparseLogin.user.type)

        val sparseSession = AbsJson.decodeFromString<PlaybackSession>("{}")
        assertEquals("", sparseSession.id)
        assertTrue(sparseSession.audioTracks.isEmpty())
    }

    @Test
    fun testCoverAndAuthorImageUrlsIncludeTokenWhenPresent() {
        val authedClient = AbsApiClient(
            baseUrl = "https://abs.example.com",
            initialTokens = Tokens(accessToken = "tok_123", refreshToken = "ref_456"),
        )
        val coverUrl = authedClient.coverUrl("item-42", width = 400, updatedAt = 1700000000L)
        assertTrue(coverUrl.contains("width=400"))
        assertTrue(coverUrl.contains("ts=1700000000"))
        assertTrue(coverUrl.contains("token=tok_123"))

        val authorUrl = authedClient.authorImageUrl("auth-9", width = 300, updatedAt = 1700000000L)
        assertTrue(authorUrl.contains("width=300"))
        assertTrue(authorUrl.contains("token=tok_123"))

        val unauthedClient = AbsApiClient(
            baseUrl = "https://abs.example.com",
            initialTokens = Tokens(accessToken = ""),
        )
        assertFalse(unauthedClient.coverUrl("item-42").contains("token="))
    }

    @Test
    fun testServerUserDeserialization() {
        val json = """
            {
              "users": [
                {
                  "id": "u-1",
                  "username": "koala",
                  "type": "root",
                  "isActive": true
                },
                {
                  "id": "u-2",
                  "username": "gast",
                  "type": "user",
                  "isActive": true
                }
              ]
            }
        """.trimIndent()

        val resp = AbsJson.decodeFromString<UsersResponse>(json)
        assertEquals(2, resp.users.size)
        assertEquals("koala", resp.users[0].username)
        assertEquals("root", resp.users[0].type)
        assertEquals("gast", resp.users[1].username)
        assertEquals("user", resp.users[1].type)
    }
}

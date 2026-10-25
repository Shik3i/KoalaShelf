package net.koalastuff.koalashelf.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AbsModelsSerializationTest {

    @Test
    fun testFlexibleGenresAndAuthors() {
        val json = """
            {
              "id": "book-1",
              "libraryId": "lib-1",
              "media": {
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

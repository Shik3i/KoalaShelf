package net.koalastuff.koalashelf.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class RealServerIntegrationTest {

    @Test
    fun testRealServerFlow() = runBlocking {
        val client = createDefaultHttpClient()
        val baseUrl = "http://localhost:13378"
        val status = runCatching { getServerStatus(baseUrl, client) }.getOrNull()
        org.junit.Assume.assumeTrue("Local test server at $baseUrl not reachable, skipping integration test", status != null)
        println("Status: $status")

        val loginRes = login(baseUrl, "koala", "51PNL-NzK481S3Nn", client)
        println("Login success: user=${loginRes.user.username}, library=${loginRes.userDefaultLibraryId}")
        val tokens = Tokens(loginRes.user.accessToken, loginRes.user.refreshToken)

        val api = AbsApiClient(baseUrl, tokens, client)
        val libs = api.libraries()
        println("Libraries: ${libs.size}")
        assertTrue(libs.isNotEmpty())

        val libId = libs.first().id
        val shelves = api.personalized(libId, limit = 16)
        println("Shelves: ${shelves.size}")
        assertTrue(shelves.isNotEmpty())

        val filterData = api.filterData(libId)
        println("FilterData: genres=${filterData.genres.size}, authors=${filterData.authors.size}")

        val items = api.libraryItems(libId, limit = 60)
        println("Items: total=${items.total}, count=${items.results.size}")
        assertTrue(items.results.isNotEmpty())

        val stats = api.listeningStats()
        println("Stats: $stats")

        // Test admin-specific endpoints
        val users = api.users()
        println("Admin fetched users: ${users.map { "${it.username} (${it.type})" }}")
        assertTrue("Users list should contain at least koala and gast", users.size >= 2)
        val gast = users.find { it.username == "gast" }
        assertNotNull("User 'gast' should exist on server", gast)

        val gastStats = api.listeningStats(gast!!.id)
        println("Admin fetched gast stats: totalTime=${gastStats.totalTime}")
        assertNotNull(gastStats)

        val gastSessions = api.listeningSessions(userId = gast.id)
        println("Admin fetched gast sessions: total=${gastSessions.total}")
        assertNotNull(gastSessions)

        val me = api.me()
        println("Me: ${me.username}, progressCount=${me.mediaProgress.size}")
    }
}

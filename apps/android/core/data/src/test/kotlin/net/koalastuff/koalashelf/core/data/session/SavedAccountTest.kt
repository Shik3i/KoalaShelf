package net.koalastuff.koalashelf.core.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedAccountTest {

    @Test
    fun testIsAdmin() {
        val rootAccount = SavedAccount(
            key = "key1",
            baseUrl = "http://localhost",
            user = AccountUser(id = "1", username = "koala", type = "root")
        )
        assertTrue(rootAccount.isAdmin)

        val adminAccount = SavedAccount(
            key = "key2",
            baseUrl = "http://localhost",
            user = AccountUser(id = "2", username = "admin", type = "admin")
        )
        assertTrue(adminAccount.isAdmin)

        val regularUser = SavedAccount(
            key = "key3",
            baseUrl = "http://localhost",
            user = AccountUser(id = "3", username = "gast", type = "user")
        )
        assertFalse(regularUser.isAdmin)

        val guestUser = SavedAccount(
            key = "key4",
            baseUrl = "http://localhost",
            user = AccountUser(id = "4", username = "anon", type = "guest")
        )
        assertFalse(guestUser.isAdmin)
    }
}

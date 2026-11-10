package il.sidewalks.reporter.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsProfileStoreTest {
    private fun tempStore(): SettingsProfileStore {
        val file = File.createTempFile("settings-profile", ".txt")
        file.delete()
        file.deleteOnExit()
        return SettingsProfileStore(file)
    }

    @Test
    fun missingFileMeansUnknown() {
        assertNull(tempStore().read())
    }

    @Test
    fun roundTrip() {
        val store = tempStore()
        store.save(listOf("subscription_key", "api_password"))
        assertEquals(listOf("subscription_key", "api_password"), store.read())
        store.save(emptyList())
        assertEquals(emptyList<String>(), store.read())
    }
}

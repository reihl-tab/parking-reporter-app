package il.sidewalks.reporter.i18n

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The SharedPreferences side of the UI language (issue #115 PR-2a), over an in-memory fake. */
class UiPrefsLanguageStoreTest {

    private class MemoryPrefs : SharedPreferences {
        val map = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? =
            if (map.containsKey(key)) map[key] as String? else defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val ops = mutableListOf<(MutableMap<String, Any?>) -> Unit>()
            override fun putString(key: String, value: String?) = apply { ops += { it[key] = value } }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { ops += { it[key] = values } }
            override fun putInt(key: String, value: Int) = apply { ops += { it[key] = value } }
            override fun putLong(key: String, value: Long) = apply { ops += { it[key] = value } }
            override fun putFloat(key: String, value: Float) = apply { ops += { it[key] = value } }
            override fun putBoolean(key: String, value: Boolean) = apply { ops += { it[key] = value } }
            override fun remove(key: String) = apply { ops += { it.remove(key) } }
            override fun clear() = apply { ops += { it.clear() } }
            override fun commit(): Boolean { ops.forEach { it(map) }; ops.clear(); return true }
            override fun apply() { commit() }
        }
    }

    @Test
    fun namesMatchTheRuling() {
        assertEquals("ui-prefs", UiPrefsLanguageStore.PREFS_NAME)
        assertEquals("language", UiPrefsLanguageStore.KEY_LANGUAGE)
    }

    @Test
    fun absentKeyReadsAsNull() {
        assertNull(UiPrefsLanguageStore(MemoryPrefs()).get())
    }

    @Test
    fun setStoresTheCodeUnderTheLanguageKey() {
        val prefs = MemoryPrefs()
        val store = UiPrefsLanguageStore(prefs)
        store.set("en")
        assertEquals("en", prefs.map["language"])
        assertEquals("en", store.get())
        assertEquals(setOf("language"), prefs.map.keys)
    }

    @Test
    fun setNullRemovesTheKey() {
        val prefs = MemoryPrefs()
        val store = UiPrefsLanguageStore(prefs)
        store.set("ar")
        assertTrue(prefs.contains("language"))
        store.set(null)
        assertFalse(prefs.contains("language"))
        assertNull(store.get())
    }

    @Test
    fun aBlankOrWrongTypedValueReadsAsNull() {
        val prefs = MemoryPrefs()
        prefs.map["language"] = " "
        assertNull(UiPrefsLanguageStore(prefs).get())
        prefs.map["language"] = 7
        assertNull(UiPrefsLanguageStore(prefs).get())
    }

    @Test
    fun theControllerOverTheRealStoreRoundTrips() {
        val prefs = MemoryPrefs()
        LanguageController(UiPrefsLanguageStore(prefs)).select("ru")
        assertEquals("ru", LanguageController(UiPrefsLanguageStore(prefs)).selected.value)
        LanguageController(UiPrefsLanguageStore(prefs)).select(null)
        assertNull(LanguageController(UiPrefsLanguageStore(prefs)).selected.value)
        assertFalse(prefs.contains("language"))
    }
}

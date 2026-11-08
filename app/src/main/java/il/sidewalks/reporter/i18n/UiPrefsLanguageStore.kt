package il.sidewalks.reporter.i18n

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * [LanguageStore] over a plain SharedPreferences file "ui-prefs", key "language".
 * Only the UI language code is stored here, never personal data. backup_rules.xml and
 * data_extraction_rules.xml already exclude the whole sharedpref domain from cloud
 * backup and device transfer.
 */
class UiPrefsLanguageStore(private val prefs: SharedPreferences) : LanguageStore {
    override fun get(): String? = try {
        prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() }
    } catch (_: ClassCastException) {
        null
    }

    override fun set(code: String?) {
        prefs.edit {
            if (code == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, code)
        }
    }

    companion object {
        const val PREFS_NAME = "ui-prefs"
        const val KEY_LANGUAGE = "language"

        fun of(context: Context): UiPrefsLanguageStore =
            UiPrefsLanguageStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}

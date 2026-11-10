package il.sidewalks.reporter.ui

import android.content.Context
import il.sidewalks.reporter.i18n.UiPrefsLanguageStore

/**
 * One-time first-run flag (issue #187). Lives in the same unauthenticated
 * "ui-prefs" store as the language choice: the flag is not sensitive, and
 * losing it only shows the welcome screen once more.
 */
class FirstRunStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun seen(): Boolean = prefs.getBoolean(KEY_FIRST_RUN_SEEN, false)

    fun markSeen() { prefs.edit().putBoolean(KEY_FIRST_RUN_SEEN, true).apply() }

    companion object {
        const val PREFS_NAME = UiPrefsLanguageStore.PREFS_NAME
        const val KEY_FIRST_RUN_SEEN = "firstRunSeen"
    }
}

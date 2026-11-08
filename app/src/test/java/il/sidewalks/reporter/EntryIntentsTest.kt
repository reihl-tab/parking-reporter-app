package il.sidewalks.reporter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for entry-point parsing (issue #149 phase 1). */
class EntryIntentsTest {

    @Test
    fun deepLinkOpensNewReport() {
        assertEquals(EntryIntents.Request.NewReport,
            EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://report/new", null, null))
        assertEquals(EntryIntents.Request.NewReport,
            EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://report", null, null))
        assertEquals(EntryIntents.Request.NewReport,
            EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://report/new/", null, null))
    }

    @Test
    fun linkMatchIsExactAndCaseSensitive() {
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "app://report/new", null, null))
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://report/newer", null, null))
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://report/new?x=1", null, null))
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "IL.SIDEWALKS.REPORTER://report/new", null, null))
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://Report/new", null, null))
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "https://il.sidewalks.reporter/report/new", null, null))
    }

    @Test
    fun tileActionOpensNewReport() {
        assertEquals(EntryIntents.Request.NewReport,
            EntryIntents.parse(EntryIntents.ACTION_TILE_NEW_REPORT, null, null, null))
    }

    @Test
    fun tileActionWinsOverSharedStream() {
        assertEquals(EntryIntents.Request.NewReport,
            EntryIntents.parse(EntryIntents.ACTION_TILE_NEW_REPORT, null, "image/jpeg", "content://media/1"))
    }

    @Test
    fun viewLinkWinsOverSharedStream() {
        assertEquals(EntryIntents.Request.NewReport,
            EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://report/new", "image/jpeg", "content://media/1"))
    }

    @Test
    fun sharedContentImagePrefillsPhoto() {
        assertEquals(EntryIntents.Request.SharedPhoto("content://media/1"),
            EntryIntents.parse("android.intent.action.SEND", null, "image/jpeg", "content://media/1"))
        assertEquals(EntryIntents.Request.SharedPhoto("content://media/2"),
            EntryIntents.parse("android.intent.action.SEND", null, "image/png", "content://media/2"))
    }

    @Test
    fun fileSchemeShareRejected() {
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, "image/jpeg", "file:///sdcard/x.jpg"))
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, "image/jpeg", "CONTENT://media/1"))
    }

    @Test
    fun shareRequiresImageTypeAndStream() {
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, "text/plain", "content://media/1"))
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, "IMAGE/JPEG", "content://media/1"))
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, null, "content://media/1"))
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, "image/jpeg", null))
    }

    @Test
    fun shareAndViewActionsAreNotInterchangeable() {
        // MIME must start with "image/", not just "image".
        assertNull(EntryIntents.parse("android.intent.action.SEND", null, "imagex/jpeg", "content://media/1"))
        // A link is only honored on VIEW.
        assertNull(EntryIntents.parse("android.intent.action.SEND", "il.sidewalks.reporter://report/new", null, null))
        // A stream is only honored on SEND.
        assertNull(EntryIntents.parse("android.intent.action.VIEW", null, "image/jpeg", "content://media/1"))
        assertNull(EntryIntents.parse("android.intent.action.MAIN", null, "image/jpeg", "content://media/1"))
    }

    @Test
    fun unknownIntentsIgnored() {
        assertNull(EntryIntents.parse("android.intent.action.VIEW", "il.sidewalks.reporter://other/new", null, null))
        assertNull(EntryIntents.parse("android.intent.action.MAIN", null, null, null))
        assertNull(EntryIntents.parse(null, null, null, null))
    }
}

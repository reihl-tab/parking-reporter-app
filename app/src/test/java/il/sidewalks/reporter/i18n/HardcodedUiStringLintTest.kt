package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.i18n.HardcodedUiStringScanner.REASON_SCRIPT
import il.sidewalks.reporter.i18n.HardcodedUiStringScanner.REASON_UI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * CI lint for user-visible hardcoded strings (issue #115). Runs inside the existing
 * testDebugUnitTest job, so it needs no workflow change.
 */
class HardcodedUiStringLintTest {

    companion object {
        /** Internal/debug sources that may keep literals. */
        val ALLOWLIST = setOf(
            "SyntheticIntakeDemo.kt",
            "LoginDiagnostic.kt",
            // Municipal protocol payloads (e.g. the city name in the API contact record),
            // never rendered as UI.
            "MunicipalPayloads.kt",
        )

        /**
         * Sources whose UI literals are not extracted yet. PR-2 of #115 extracts them
         * and empties this set; it must never grow.
         */
        val PENDING = setOf(
            "AppShell.kt",
            "ReviewScreen.kt",
            "SettingsScreen.kt",
            "CaptureScreen.kt",
            "ReportsScreen.kt",
            "MainActivity.kt",
            "ReportCategory.kt",
            "HebrewPresentation.kt",
        )

        private const val MAX_PENDING = 8
    }

    private fun mainSources(): List<File> {
        val roots = listOf("src/main/java", "src/main/kotlin")
            .mapNotNull { rel -> listOf(File(rel), File("app", rel)).firstOrNull { it.isDirectory } }
        return roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sortedBy { it.path }
    }

    private fun scanSource(src: String) = HardcodedUiStringScanner.scan("Synthetic.kt", src)

    @Test
    fun mainSourcesHaveNoHardcodedUiStrings() {
        val files = mainSources()
        assertTrue("scanned too few sources (${files.size}); wrong directory?", files.size >= 25)
        val findings = files
            .filter { it.name !in ALLOWLIST && it.name !in PENDING }
            .flatMap { HardcodedUiStringScanner.scan(it.path, it.readText(Charsets.UTF_8)) }
        if (findings.isNotEmpty()) {
            fail(
                "Hardcoded user-visible strings; move them to assets/i18n/strings.json and read them with t():\n" +
                    findings.joinToString("\n"),
            )
        }
    }

    @Test
    fun pendingListIsExactAndDoesNotGrow() {
        assertTrue("PENDING may only shrink (PR-2 empties it)", PENDING.size <= MAX_PENDING)
        assertEquals(
            setOf(
                "AppShell.kt", "ReviewScreen.kt", "SettingsScreen.kt", "CaptureScreen.kt",
                "ReportsScreen.kt", "MainActivity.kt", "ReportCategory.kt", "HebrewPresentation.kt",
            ),
            PENDING,
        )
    }

    @Test
    fun pendingAndAllowlistedFilesExistAndPendingStillNeedsExtraction() {
        val byName = mainSources().groupBy { it.name }
        for (name in ALLOWLIST + PENDING) {
            assertEquals("$name must name exactly one main source", 1, byName[name]?.size ?: 0)
        }
        for (name in PENDING) {
            val file = byName.getValue(name).single()
            val found = HardcodedUiStringScanner.scan(file.path, file.readText(Charsets.UTF_8))
            assertTrue("$name has no findings left; remove it from PENDING", found.isNotEmpty())
        }
    }

    @Test
    fun selfTestHardcodedHebrewTextIsReported() {
        val f = scanSource("@Composable fun A() {\n    Text(\"שלום\")\n}\n")
        assertEquals(1, f.size)
        assertEquals(2, f[0].line)
        assertEquals("שלום", f[0].literal)
        assertTrue(f[0].toString().startsWith("Synthetic.kt:2: "))
    }

    @Test
    fun selfTestTranslatedTextIsNotReported() {
        assertEquals(emptyList<Any>(), scanSource("@Composable fun A() { Text(t(\"common.ok\")) }"))
        assertEquals(emptyList<Any>(), scanSource("val s = strings.t(\"common.ok\")\nText(text = s)"))
        assertEquals(emptyList<Any>(), scanSource("Text(strings.tp(\"common.reports_count\", n))"))
    }

    @Test
    fun scriptLiteralsAreReportedAnywhere() {
        for (word in listOf("שלום", "مرحبا", "Привет", "ሰላም")) {
            val f = scanSource("val greeting = \"$word\"")
            assertEquals(word, 1, f.size)
            assertEquals(REASON_SCRIPT, f[0].reason)
        }
        assertEquals(1, scanSource("val r = \"\"\"שלום\nעולם\"\"\"").size)
        assertEquals(1, scanSource("foo(\"x\", listOf(\"a\", \"מחר\"))").size)
    }

    @Test
    fun latinLiteralsInUiPositionsAreReported() {
        val src = """
            Text("Submit report")
            OutlinedTextField(value = v, onValueChange = {}, label = { Text(text = "Plate") })
            TextField(placeholder = "Type here")
            Icon(Icons.Default.Close, contentDescription = "Close")
            Dialog(title = "Are you sure?")
            Button(onClick = {}) { Text(
                "Multi line"
            ) }
        """.trimIndent()
        val f = scanSource(src)
        assertEquals(listOf("Submit report", "Plate", "Type here", "Close", "Are you sure?", "Multi line"), f.map { it.literal })
        assertEquals(listOf(1, 2, 3, 4, 5, 7), f.map { it.line })
        assertTrue(f.all { it.reason == REASON_UI })
    }

    @Test
    fun templatesAreScanned() {
        assertEquals(1, scanSource("Text(\"\$count דיווחים\")").size)
        assertEquals(1, scanSource("Text(\"Total: \${n}\")").size)
        assertEquals(0, scanSource("Text(\"\${n}\")").size)
        assertEquals(0, scanSource("Text(\"\$a / \$b\")").size)
        // A literal nested inside a template expression is still scanned.
        assertEquals(1, scanSource("Text(\"\${if (ok) \"כן\" else x}\")").size)
    }

    @Test
    fun commentsCharLiteralsAndTechnicalStringsAreIgnored() {
        val src = """
            // Text("שלום") in a line comment
            /* Text("שלום") in a block /* nested "שלום" */ comment */
            /** KDoc mentioning "מאגר" */
            val quote = '"'
            val q2 = '\''
            val re = Regex("^[0-9]{2}-[0-9]{3}-[0-9]{2}${'$'}")
            val mime = intent.setType("image/jpeg")
            Text(text = "image/jpeg")
            Text("•")
            Text("12:34")
            Text("common.ok")
            composable(route = "settings") {}
            Icon(painter, contentDescription = null)
            Log.d("ReporterTag", "x")
            Log.w(TAG, "y")
            private const val TAG = "Capture"
            val x = a.text == "Hello"
        """.trimIndent()
        assertEquals(emptyList<Any>(), scanSource(src))
    }

    @Test
    fun scannerKeepsLineNumbersAfterCommentsAndRawStrings() {
        val src = "/*\n\n*/\nval a = \"\"\"\nraw\n\"\"\"\nText(\"Hi there\")\n"
        val f = scanSource(src)
        assertEquals(1, f.size)
        assertEquals(7, f[0].line)
    }

    @Test
    fun technicalPredicate() {
        assertTrue(HardcodedUiStringScanner.isTechnical(""))
        assertTrue(HardcodedUiStringScanner.isTechnical("12-345-67"))
        assertTrue(HardcodedUiStringScanner.isTechnical("image/*"))
        assertTrue(HardcodedUiStringScanner.isTechnical("review.submit_button"))
        assertTrue(HardcodedUiStringScanner.isTechnical("reports/{id}"))
        assertTrue(!HardcodedUiStringScanner.isTechnical("OK"))
        assertTrue(!HardcodedUiStringScanner.isTechnical("Save draft"))
    }
}

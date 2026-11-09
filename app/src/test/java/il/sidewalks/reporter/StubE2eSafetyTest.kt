package il.sidewalks.reporter

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The synthetic E2E must not grow a real-endpoint mode or alter private lookup files. */
class StubE2eSafetyTest {
    @Test fun syntheticE2eIsStubOnlyAndInjectsItsDirectory() {
        val file = listOf(
            File("src/androidTest/java/il/sidewalks/reporter/FullFlowE2eTest.kt"),
            File("app/src/androidTest/java/il/sidewalks/reporter/FullFlowE2eTest.kt"),
        ).first { it.isFile }
        val source = file.readText()
        assertFalse(source.contains("e2eLive"))
        assertFalse(source.contains("SecureSettings"))
        assertFalse(source.contains("addresses.json"))
        assertTrue(source.contains("streetDirectoryLoader = { syntheticStreetDirectory() }"))
        assertTrue(source.contains("OkHttpMunicipalClient.forTesting(server.url"))
        assertFalse(source.contains("deleteRecursively"))
        assertTrue(source.contains("DELETE FROM reports WHERE stableId = ? AND originalSha256 = ?"))
        assertTrue(source.contains("findPhoto(hash) } == null"))
        val androidTests = file.parentFile.walkTopDown().filter { it.extension in setOf("kt", "java") }.toList()
        androidTests.forEach { testFile ->
            val text = testFile.readText()
            assertFalse("Live arguments in ${testFile.name}", text.contains("getArguments("))
            assertFalse("Production client in ${testFile.name}", Regex("OkHttpMunicipalClient\\s*\\(").containsMatchIn(text))
        }
    }
}

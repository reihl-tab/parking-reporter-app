package il.sidewalks.reporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The loopback-cleartext allowance exists ONLY in app/src/debug for the
 * instrumented E2E stub server. The main (release) manifest must keep
 * cleartext fully forbidden and declare no networkSecurityConfig; if the
 * debug-only allowance ever leaks into a production build, this test fails.
 */
class MainManifestGuardTest {
    @Test
    fun mainManifestForbidsCleartextAndDeclaresNoSecurityConfig() {
        val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(manifest.contains("android:networkSecurityConfig"))
    }

    @Test
    fun debugManifestScopesCleartextToLoopbackOnly() {
        val config = File("src/debug/res/xml/network_security_config.xml").readText(Charsets.UTF_8)
        assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertTrue(config.contains(">localhost</domain>"))
        assertTrue(config.contains(">127.0.0.1</domain>"))
    }
}

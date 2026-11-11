package funlauncher.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchOptionsTest {
    @Test
    fun quotedPropertiesPreserveSpacesAndWindowsPaths() {
        val options = validateLaunchOptions("""-Dpath="C:\Program Files\Game" -XX:+UseG1GC""", "TOKEN=a=b\nEMPTY=", 4096)
        assertTrue(options.valid)
        assertEquals(listOf("-Dpath=C:\\Program Files\\Game", "-XX:+UseG1GC"), options.arguments)
        assertEquals(mapOf("TOKEN" to "a=b", "EMPTY" to ""), options.environment)
    }

    @Test
    fun malformedArgumentsAndHeapOverridesAreRejected() {
        listOf("nonsense", "-D", "-XX:", "-Xmx8G", "-Xms8G", "-Xmsnope", "-jar bad.jar", "@args.txt", "-Dname=\"unclosed").forEach {
            assertFalse(validateLaunchOptions(it, "", 4096).valid, it)
        }
        assertTrue(validateLaunchOptions("-Xms512M -Dflag", "", 4096).valid)
    }

    @Test
    fun malformedDuplicateAndIndirectJvmEnvironmentIsRejected() {
        listOf("nonsense", "=value", "BAD NAME=value", "A=1\nA=2", "JAVA_TOOL_OPTIONS=-Xmx8G", "A=\u0000").forEach {
            assertFalse(validateLaunchOptions("", it, 4096).valid, it)
        }
    }

    @Test
    fun riskyOptionsRequireReviewButRemainValid() {
        val options = validateLaunchOptions("-javaagent:agent.jar -XX:OnError=command", "PATH=custom", 4096)
        assertTrue(options.valid)
        assertEquals(3, options.risks.size)
    }

    @Test
    fun emptyFieldsAreValid() {
        assertTrue(validateLaunchOptions("", "", 4096).valid)
    }
}

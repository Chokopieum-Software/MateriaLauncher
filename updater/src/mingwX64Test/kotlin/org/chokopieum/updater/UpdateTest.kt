@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.chokopieum.updater

import kotlin.test.*
import kotlinx.cinterop.*
import updater.win32.*

class UpdateTest {
    @Test fun nativeWindowReceivesWorkerProgress() {
        val ready = "native-ui-ready-${mu_pid()}"
        try { assertTrue(mu_test_ui(ready) != 0) }
        finally { mu_delete(ready) }
    }

    @Test fun systemTarListsAndExtractsZipWithUnicodePath() {
        val fixture = platform.posix.getenv("MATERIA_UPDATER_TEST_ZIP")!!.toKString()
        val root = "native-archive-тест-更新-${mu_pid()}"
        assertTrue(mu_mkdir(root) != 0)
        try {
            assertTrue(mu_mkdir("$root/staged") != 0)
            fun read(path: String): String {
                val pointer = mu_read(path)!!
                return try { pointer.toKString() } finally { mu_free(pointer) }
            }
            fun tar(mode: Int, directory: String, log: String) {
                val result = mu_tar(fixture, directory, log, mode)
                assertTrue(result != 0, "tar mode=$mode failed (Win32 ${mu_error()}): ${read(log)}")
            }
            tar(0, root, "$root/names")
            tar(1, root, "$root/details")
            validateListing(read("$root/names"), read("$root/details"))
            tar(2, "$root/staged", "$root/extract.log")
            assertEquals("test-payload", read("$root/staged/app/test.jar"))
        } finally { mu_delete(root) }
    }

    @Test fun rejectsTraversalAndLinks() {
        val details = "-rw-r--r--  0 user group 123 Jan 1 00:00 app/test.jar\n"
        validateListing("app/test.jar\n", details)
        for (name in listOf("../outside", "app/../../outside", "C:/outside", "app\\outside", "app/file:stream", "/app/file", "app/dir./file")) {
            assertFails { validateListing(name, details) }
        }
        assertFails { validateListing("app/test.jar", "lrwxrwxrwx  0 user group 0 Jan 1 00:00 app/test.jar -> outside") }
    }

    @Test fun restoresOldFilesWhenReplacementFailsHalfway() {
        val root = "native-update-test-${mu_pid()}"
        assertTrue(mu_mkdir(root) != 0)
        try {
            for (directory in listOf("target", "staged")) assertTrue(mu_mkdir("$root/$directory") != 0)
            for (name in listOf("app", "runtime", "updater", "materia-launcher.exe", "update.properties")) {
                assertTrue(mu_write("$root/target/$name", "old-$name") != 0)
            }
            // Failure after the first new component was installed must roll back every old file.
            assertTrue(mu_write("$root/staged/app", "new-app") != 0)
            assertFails { replace("$root/target", "$root/staged", "$root/backup") }
            for (name in listOf("app", "runtime", "updater", "materia-launcher.exe", "update.properties")) {
                val pointer = mu_read("$root/target/$name")!!
                try { assertEquals("old-$name", pointer.toKString()) } finally { mu_free(pointer) }
            }
        } finally { mu_delete(root) }
    }
}

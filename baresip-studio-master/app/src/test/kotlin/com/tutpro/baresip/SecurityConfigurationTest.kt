package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File

class SecurityConfigurationTest {

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "app/src/main/AndroidManifest.xml").exists()) {
                return current
            }
            current = current.parentFile
        }
        throw IllegalStateException("Cannot find project root with app/src/main/AndroidManifest.xml")
    }

    @Test
    fun manifestUnexportsTaskReceiverAndEnforcesSecurityFlags() {
        val root = findProjectRoot()
        val manifestFile = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val parser = KXmlParser().apply {
            setInput(manifestFile.inputStream(), "UTF-8")
        }

        var foundTaskReceiver = false
        var taskReceiverExported: String? = null
        var taskReceiverToolsIgnore: String? = null
        var allowBackup: String? = null
        var fullBackupContent: String? = null
        var fileProviderExported: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "application" -> {
                        allowBackup = parser.getAttributeValue(null, "allowBackup")
                            ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "allowBackup")
                        fullBackupContent = parser.getAttributeValue(null, "fullBackupContent")
                            ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "fullBackupContent")
                    }
                    "receiver" -> {
                        val name = parser.getAttributeValue(null, "name")
                            ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        if (name == ".TaskReceiver" || name == "com.tutpro.baresip.TaskReceiver") {
                            foundTaskReceiver = true
                            taskReceiverExported = parser.getAttributeValue(null, "exported")
                                ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "exported")
                            taskReceiverToolsIgnore = parser.getAttributeValue("http://schemas.android.com/tools", "ignore")
                        }
                    }
                    "provider" -> {
                        val name = parser.getAttributeValue(null, "name")
                            ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        if (name == "androidx.core.content.FileProvider") {
                            fileProviderExported = parser.getAttributeValue(null, "exported")
                                ?: parser.getAttributeValue("http://schemas.android.com/apk/res/android", "exported")
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        assertTrue("TaskReceiver must be declared in AndroidManifest.xml", foundTaskReceiver)
        assertEquals("TaskReceiver must be android:exported=\"false\" (SEC-01)", "false", taskReceiverExported)
        assertNotEquals("tools:ignore=\"ExportedReceiver\" must not be present", "ExportedReceiver", taskReceiverToolsIgnore)
        assertEquals("allowBackup must be false", "false", allowBackup)
        assertEquals("fullBackupContent must be false", "false", fullBackupContent)
        assertEquals("FileProvider must be android:exported=\"false\"", "false", fileProviderExported)
    }

    @Test
    fun fileProviderPathsDenyRootDirectory() {
        val root = findProjectRoot()
        val filePathsFile = File(root, "app/src/main/res/xml/file_paths.xml")
        assertTrue("file_paths.xml must exist", filePathsFile.exists())

        val parser = KXmlParser().apply {
            setInput(filePathsFile.inputStream(), "UTF-8")
        }

        val declaredPaths = mutableListOf<Pair<String, String>>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "files-path") {
                val name = parser.getAttributeValue(null, "name") ?: ""
                val path = parser.getAttributeValue(null, "path") ?: ""
                declaredPaths.add(name to path)
            }
            eventType = parser.next()
        }

        assertTrue("At least one files-path must be declared", declaredPaths.isNotEmpty())

        // Strict verification: NO root path exposure
        for ((name, path) in declaredPaths) {
            assertNotEquals("files-path '$name' must not expose entire files directory with path='.'", ".", path)
            assertNotEquals("files-path '$name' must not expose entire files directory with empty path", "", path)
        }

        // Must explicitly declare mms_download.dat or mms/ for MMS compatibility
        assertTrue("MMS download path must be declared", declaredPaths.any { it.second == "mms_download.dat" || it.second.startsWith("mms") })

        // Check that sensitive root files are NOT accessible
        val sensitiveFiles = listOf("accounts", "swusip-directory.db", "swusip-directory.db-wal", "swusip-directory.db-shm")
        for (sensitive in sensitiveFiles) {
            val allowed = declaredPaths.any { (_, path) ->
                path == sensitive || (path.endsWith("/") && sensitive.startsWith(path))
            }
            assertFalse("Sensitive file '$sensitive' must not be accessible via FileProvider paths", allowed)
        }
    }
}

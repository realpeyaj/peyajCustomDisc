package com.peyaj.jukeboxweb.config

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigMigratorTest {

    private val templateText = """
# peyajCustomDisc Configuration

# The port for the web interface (Disc Creator).
# Default: 8080
web-port: 8080

# Security Admin Password for direct browser login to Disc Creator.
admin-password: "changeme"

# The Public URL of this plugin's web server.
public-url: "http://localhost:8080"

# Should the plugin automatically push the resource pack in real-time?
auto-update-pack: true

# ========================================
# Java Resource Pack Delivery Settings
# ========================================
resource-pack:
  # Whether to automatically send the resource pack to Java players when they join.
  send-on-join: true
  # Whether this pack should replace ALL existing server resource packs.
  replace-existing: false
  # Whether the resource pack prompt is mandatory to accept to play.
  required: false
  # Optional custom prompt message.
  prompt: ""

# Auto-reload geyser
auto-reload-geyser: true

# Proxy / HAProxy Support
haproxy-support: false

# Should the plugin check for new releases on Modrinth?
check-updates: true

# ========================================
# WorldGuard Region Music (Optional)
# ========================================
region-music:
  # example_region:
  #   discs:
  #     - "example_disc_1"

# ========================================
# Jukebox Music Settings
# ========================================
jukebox:
  # Default volume
  volume: 1.0
  # Default pitch
  pitch: 1.0
  # Sound category
  sound-category: "RECORDS"
  # Range
  range: 64.0
  # Enable note particles
  enable-particles: true
  # Particle style
  particle-type: "MATCH"
  # Whether jukeboxes automatically eject the disc onto the block
  auto-eject: true

# ========================================
# Catalog Settings
# ========================================
catalog:
  # Duration in seconds
  preview-duration: 15
""".trimIndent()

    @Test
    fun testNoChangesWhenConfigIsCurrent() {
        val (migrated, addedKeys) = ConfigMigrator.migrate(templateText, templateText)
        assertTrue(addedKeys.isEmpty(), "Should not add keys to an already complete config")
        assertEquals(templateText, migrated)
    }

    @Test
    fun testMigrateMissingSubkeysInExistingSections() {
        val userConfig = """
# User custom config
web-port: 9090
admin-password: "secretpassword"
public-url: "http://mc.example.com:9090"
auto-update-pack: false

resource-pack:
  # My custom setting
  send-on-join: false

auto-reload-geyser: true
haproxy-support: false
check-updates: true

region-music:
  # my region

jukebox:
  volume: 0.8
  pitch: 1.0
  sound-category: "RECORDS"
  range: 48.0
  enable-particles: true
  particle-type: "HEART"

catalog:
  preview-duration: 20
""".trimIndent()

        val (migrated, addedKeys) = ConfigMigrator.migrate(userConfig, templateText)

        // Missing subkeys in resource-pack: replace-existing, required, prompt
        assertTrue(addedKeys.contains("resource-pack.replace-existing"))
        assertTrue(addedKeys.contains("resource-pack.required"))
        assertTrue(addedKeys.contains("resource-pack.prompt"))

        // Missing subkey in jukebox: auto-eject
        assertTrue(addedKeys.contains("jukebox.auto-eject"))

        // User custom values MUST be preserved
        assertTrue(migrated.contains("web-port: 9090"))
        assertTrue(migrated.contains("admin-password: \"secretpassword\""))
        assertTrue(migrated.contains("send-on-join: false"))
        assertTrue(migrated.contains("volume: 0.8"))
        assertTrue(migrated.contains("particle-type: \"HEART\""))
        assertTrue(migrated.contains("preview-duration: 20"))

        // New keys and comments MUST be present
        assertTrue(migrated.contains("replace-existing: false"))
        assertTrue(migrated.contains("auto-eject: true"))
        assertTrue(migrated.contains("# Whether jukeboxes automatically eject the disc onto the block"))
    }

    @Test
    fun testMigrateMissingTopLevelSections() {
        val oldConfig = """
web-port: 8080
admin-password: "changeme"
public-url: "http://localhost:8080"
auto-update-pack: true
""".trimIndent()

        val (migrated, addedKeys) = ConfigMigrator.migrate(oldConfig, templateText)

        assertTrue(addedKeys.contains("resource-pack"))
        assertTrue(addedKeys.contains("auto-reload-geyser"))
        assertTrue(addedKeys.contains("haproxy-support"))
        assertTrue(addedKeys.contains("check-updates"))
        assertTrue(addedKeys.contains("region-music"))
        assertTrue(addedKeys.contains("jukebox"))
        assertTrue(addedKeys.contains("catalog"))

        // Ensure newly appended sections contain their full structure
        assertTrue(migrated.contains("haproxy-support: false"))
        assertTrue(migrated.contains("check-updates: true"))
        assertTrue(migrated.contains("catalog:"))
        assertTrue(migrated.contains("preview-duration: 15"))
        assertTrue(migrated.contains("auto-eject: true"))
    }

    @Test
    fun testUpdateConfigFileWorksWithRealFile() {
        val tempFile = File.createTempFile("test_config", ".yml")
        tempFile.deleteOnExit()

        tempFile.writeText("""
web-port: 8080
admin-password: "changeme"
public-url: "http://localhost:8080"
auto-update-pack: true
""".trimIndent())

        val templateStream = templateText.byteInputStream()
        val modified = ConfigMigrator.updateConfigFile(tempFile, templateStream)

        assertTrue(modified)
        val result = tempFile.readText()
        assertTrue(result.contains("catalog:"))
        assertTrue(result.contains("haproxy-support: false"))

        // Second run with updated file should produce 0 modifications
        val templateStream2 = templateText.byteInputStream()
        val modifiedAgain = ConfigMigrator.updateConfigFile(tempFile, templateStream2)
        assertFalse(modifiedAgain)
    }
}

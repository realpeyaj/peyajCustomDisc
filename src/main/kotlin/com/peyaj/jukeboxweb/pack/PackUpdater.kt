package com.peyaj.jukeboxweb.pack

import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID

object PackUpdater : Listener {

    // Helper to extract clean hash (bytes to hex)
    // PackGenerator has getPackHash() returning Hex String, perfect.
    
    // Helper Check
    private fun isGeyserPlayer(uuid: UUID): Boolean {
        // Floodgate UUIDs usually start with 00000000-0000-0000-
        // Java UUIDs are Version 4 or 3. Most significant bits 0 is a strong indicator of Floodgate.
        return uuid.mostSignificantBits == 0L
    }

    fun updateAllPlayers(plugin: PeyajCustomDisc) {
        // 1. Geyser Update (Attempts to copy pack to Geyser folder)
        // This is always safe to run as file IO. Reload is guarded inside.
        updateGeyserPack(plugin)
    
        // 2. Java Auto-Update
        // Explicitly controlled by config.
        if (plugin.config.getBoolean("auto-update-pack", true)) {
            val urlBase = plugin.config.getString("public-url", "http://localhost:8080")?.trimEnd('/') ?: return
            val zipUrl = "$urlBase/download/pack"
            val hash = plugin.packGenerator.getPackHash() // Hex String
            
            if (hash.isNotEmpty()) {
                val msg = Component.text("Resource Pack Updated. Loading...", NamedTextColor.GOLD)
            
                for (player in plugin.server.onlinePlayers) {
                    // Skip Geyser players for Java pack updates
                    if (isGeyserPlayer(player.uniqueId)) continue

                    player.sendMessage(msg)
                    try {
                        val packInfo = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo()
                            .id(java.util.UUID.nameUUIDFromBytes(zipUrl.toByteArray()))
                            .uri(java.net.URI.create(zipUrl))
                            .hash(hash)
                            .build()
                        val request = net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                            .packs(packInfo)
                            .build()
                        player.sendResourcePacks(request)
                    } catch (e: Exception) {
                        player.setResourcePack(zipUrl)
                    }
                }
            }
        }
    }
    
    private fun updateGeyserPack(plugin: PeyajCustomDisc) {
        val path = plugin.config.getString("geyser-packs-path", "plugins/Geyser-Spigot/packs") ?: return
        if (path.isEmpty()) return
        
        val geyserFolder = java.io.File(path)
        val actualFolder = if (geyserFolder.isAbsolute) geyserFolder else java.io.File(plugin.dataFolder.parentFile.parentFile, path).canonicalFile 
        
        if (!actualFolder.exists()) return
        
        val source = plugin.packGenerator.getBedrockPackFile()
        if (source.exists()) {
             try {
                 source.copyTo(java.io.File(actualFolder, "peyajCD-Bedrock.mcpack"), overwrite = true)
                 // Quiet log, no broadcast to avoid spam if manual
                 plugin.logger.info("Bedrock Pack updated in Geyser folder.")
                 
                 // Reload is SEPARATE now.
                 if (plugin.config.getBoolean("auto-reload-geyser", false)) {
                     plugin.server.scheduler.runTask(plugin, Runnable {
                         plugin.logger.info("Reloading Geyser...")
                         plugin.server.dispatchCommand(plugin.server.consoleSender, "geyser reload")
                     })
                 }
             } catch (e: Exception) {
                 plugin.logger.warning("Failed to copy pack to Geyser: ${e.message}")
             }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val plugin = PeyajCustomDisc.instance
        if (isGeyserPlayer(event.player.uniqueId)) return // Skip for Bedrock
        
        if (!plugin.config.getBoolean("auto-update-pack", true)) return
    
        val urlBase = plugin.config.getString("public-url", "http://localhost:8080")?.trimEnd('/') ?: return
        val zipUrl = "$urlBase/download/pack"
        val hash = plugin.packGenerator.getPackHash()
        
        if (hash.isNotEmpty()) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                try {
                    val packInfo = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo()
                        .id(java.util.UUID.nameUUIDFromBytes(zipUrl.toByteArray()))
                        .uri(java.net.URI.create(zipUrl))
                        .hash(hash)
                        .build()
                    val request = net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                        .packs(packInfo)
                        .build()
                    event.player.sendResourcePacks(request)
                } catch (e: Exception) {
                     event.player.setResourcePack(zipUrl)
                }
            }, 20L)
        }
    }
    
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

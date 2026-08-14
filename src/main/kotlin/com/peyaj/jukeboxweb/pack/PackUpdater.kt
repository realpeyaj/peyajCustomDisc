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
    private fun isBedrockPlayer(player: org.bukkit.entity.Player): Boolean {
        val uuid = player.uniqueId
        
        // 1. Geyser API Check
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") || 
            org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Geyser-Paper") ||
            org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
            try {
                val apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
                val api = apiClass.getMethod("api").invoke(null)
                val isBedrock = apiClass.getMethod("isBedrockPlayer", UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }
        
        // 2. Floodgate API Check
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                val api = apiClass.getMethod("getInstance").invoke(null)
                val isBedrock = apiClass.getMethod("isFloodgatePlayer", UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }
        
        // 3. Fallback check: Floodgate UUIDs often have 0L mostSignificantBits
        return uuid.mostSignificantBits == 0L
    }

    private val JAVA_PACK_UUID: UUID = UUID.nameUUIDFromBytes("peyajcustomdisc:java-pack".toByteArray())

    fun updateAllPlayers(plugin: PeyajCustomDisc) {
        // 1. Geyser Update (Attempts to copy pack to Geyser folder & reload Geyser if enabled)
        updateGeyserPack(plugin, forceReload = true)
    
        // 2. Java Auto-Update
        if (plugin.config.getBoolean("auto-update-pack", true)) {
            val urlBase = plugin.config.getString("public-url", "http://localhost:8080")?.trimEnd('/') ?: return
            val zipUrl = "$urlBase/download/pack"
            val hash = plugin.packGenerator.getPackHash() // Hex String
            
            if (hash.isNotEmpty()) {
                val msg = Component.text("Resource Pack Updated. Loading...", NamedTextColor.GOLD)
            
                for (player in plugin.server.onlinePlayers) {
                    // Skip Geyser/Bedrock players for Java pack updates
                    if (isBedrockPlayer(player)) continue

                    player.sendMessage(msg)
                    try {
                        val packInfo = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo()
                            .id(JAVA_PACK_UUID)
                            .uri(java.net.URI.create(zipUrl))
                            .hash(hash)
                            .build()
                        val replace = plugin.config.getBoolean("resource-pack.replace-existing", false)
                        val requestBuilder = net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                            .packs(packInfo)
                            .replace(replace)
                        
                        val promptText = plugin.config.getString("resource-pack.prompt", "")?.trim()
                        if (!promptText.isNullOrEmpty()) {
                            requestBuilder.prompt(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(promptText))
                        }
                        if (plugin.config.getBoolean("resource-pack.required", false)) {
                            requestBuilder.required(true)
                        }

                        player.sendResourcePacks(requestBuilder.build())
                    } catch (e: Throwable) {
                        try {
                            player.setResourcePack(zipUrl, hexStringToByteArray(hash))
                        } catch (e2: Throwable) {
                            player.setResourcePack(zipUrl)
                        }
                    }
                }
            }
        }
    }

    fun updateGeyserPack(plugin: PeyajCustomDisc, forceReload: Boolean = false) {
        val configuredPath = plugin.config.getString("geyser-packs-path", "plugins/Geyser-Spigot/packs") ?: "plugins/Geyser-Spigot/packs"
        val serverRoot = plugin.dataFolder.parentFile.parentFile

        // Candidates for Geyser packs directory
        val candidates = listOf(
            configuredPath,
            "plugins/Geyser-Spigot/packs",
            "plugins/Geyser-Paper/packs",
            "plugins/Geyser/packs",
            "plugins/Geyser-Standalone/packs"
        )

        var targetFolder: java.io.File? = null
        for (path in candidates) {
            val file = if (java.io.File(path).isAbsolute) java.io.File(path) else java.io.File(serverRoot, path)
            if (file.exists()) {
                targetFolder = file
                break
            } else if (file.parentFile != null && file.parentFile.exists()) {
                file.mkdirs()
                targetFolder = file
                break
            }
        }

        val actualFolder = targetFolder ?: return
        if (!actualFolder.exists()) actualFolder.mkdirs()
        
        val source = plugin.packGenerator.getBedrockPackFile()
        if (source.exists()) {
             try {
                 source.copyTo(java.io.File(actualFolder, "peyajCD-Bedrock.mcpack"), overwrite = true)
                 plugin.logger.info("Bedrock Pack updated in Geyser folder: ${actualFolder.path}")
                 
                 if (forceReload && plugin.config.getBoolean("auto-reload-geyser", false)) {
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
        if (isBedrockPlayer(event.player)) return // Skip Java pack prompt for Bedrock
        
        val sendOnJoin = plugin.config.getBoolean("resource-pack.send-on-join", plugin.config.getBoolean("auto-update-pack", true))
        if (!sendOnJoin) return
    
        val urlBase = plugin.config.getString("public-url", "http://localhost:8080")?.trimEnd('/') ?: return
        val zipUrl = "$urlBase/download/pack"
        val hash = plugin.packGenerator.getPackHash()
        
        if (hash.isNotEmpty()) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                try {
                    val packInfo = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo()
                        .id(JAVA_PACK_UUID)
                        .uri(java.net.URI.create(zipUrl))
                        .hash(hash)
                        .build()
                    val replace = plugin.config.getBoolean("resource-pack.replace-existing", false)
                    val requestBuilder = net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest()
                        .packs(packInfo)
                        .replace(replace)
                    
                    val promptText = plugin.config.getString("resource-pack.prompt", "")?.trim()
                    if (!promptText.isNullOrEmpty()) {
                        requestBuilder.prompt(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(promptText))
                    }
                    if (plugin.config.getBoolean("resource-pack.required", false)) {
                        requestBuilder.required(true)
                    }

                    event.player.sendResourcePacks(requestBuilder.build())
                } catch (e: Throwable) {
                    plugin.logger.fine("Adventure sendResourcePacks fallback triggered: ${e.message}")
                    try {
                        event.player.setResourcePack(zipUrl, hexStringToByteArray(hash))
                    } catch (e2: Throwable) {
                        try {
                            event.player.setResourcePack(zipUrl)
                        } catch (e3: Throwable) {
                            plugin.logger.warning("Failed to send resource pack to ${event.player.name}: ${e3.message}")
                        }
                    }
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

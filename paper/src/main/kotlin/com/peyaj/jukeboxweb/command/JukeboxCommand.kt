package com.peyaj.jukeboxweb.command

import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class JukeboxCommand(private val plugin: PeyajCustomDisc) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("pjcustomdisc.admin")) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("peyajCustomDisc Help", NamedTextColor.AQUA))
            sender.sendMessage(Component.text("/disc give <player> <id> - Give a custom disc", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc list - List all available discs", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc play <id> - Play a disc everywhere (Portable)", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc stop - Stop all disc music", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region set <region> <disc> [loop] [stereo] - Set region music", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region loop <region> [on|off] - Toggle or set music repeating", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region stereo <region> [on|off] - Toggle or set stereo background audio", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region remove <region> - Remove region music", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region list - List region mappings", NamedTextColor.YELLOW))
            if (sender.hasPermission("pjcustomdisc.admin")) {
                sender.sendMessage(Component.text("/disc add <id> <url> <name> <author> - Add a custom disc", NamedTextColor.YELLOW))
                sender.sendMessage(Component.text("/disc delete <id> - Delete a custom disc", NamedTextColor.YELLOW))
                sender.sendMessage(Component.text("/disc web - Generate Admin Login Link", NamedTextColor.RED))
            }
            return true
        }

        if (args[0].equals("web", ignoreCase = true)) {
            if (sender !is Player) {
                 sender.sendMessage(Component.text("Players only.", NamedTextColor.RED))
                 return true
            }
            if (!sender.hasPermission("jukeboxweb.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }
            
            val token = com.peyaj.jukeboxweb.auth.AuthManager.createLoginToken(sender.uniqueId)
            val urlBase = plugin.config.getString("public-url", "http://localhost:8080")?.trimEnd('/') ?: "http://localhost:8080"
            val loginLink = "$urlBase/login?token=$token"
            
            val message = Component.text("▶ Click here to login to Disc Creator", NamedTextColor.AQUA)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(loginLink, NamedTextColor.GRAY)))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(loginLink))
            
            sender.sendMessage(message)
            sender.sendMessage(Component.text("Note: This link is one-time use.", NamedTextColor.DARK_GRAY))
            return true
        }

        if (args[0].equals("give", ignoreCase = true)) {
            if (!sender.hasPermission("jukeboxweb.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }
            if (args.size < 3) {
                sender.sendMessage(Component.text("Usage: /disc give <player> <disc_id>", NamedTextColor.RED))
                return true
            }

            val target = plugin.server.getPlayer(args[1])
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
                return true
            }

            val discId = args[2]
            val item = plugin.discManager.createDiscItem(discId)
            
            if (item != null) {
                target.inventory.addItem(item)
                sender.sendMessage(Component.text("Given disc $discId to ${target.name}", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(Component.text("Disc $discId not found.", NamedTextColor.RED))
            }
            return true
        }
        
        if (args[0].equals("list", ignoreCase = true)) {
            val discs = plugin.discManager.getAllDiscs()
            sender.sendMessage(Component.text("Loaded Discs (${discs.size}):", NamedTextColor.GOLD))
            discs.forEach { disc ->
                val line = Component.text("- ", NamedTextColor.GRAY)
                    .append(Component.text(disc.id, NamedTextColor.YELLOW))
                    .append(Component.text(" (${disc.name})", NamedTextColor.DARK_GRAY))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Click to get ${disc.name}", NamedTextColor.AQUA)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/disc give ${sender.name} ${disc.id}"))
                
                sender.sendMessage(line)
            }
            return true
        }

        if (args[0].equals("play", ignoreCase = true)) {
            if (sender !is Player) {
                sender.sendMessage(Component.text("Players only.", NamedTextColor.RED))
                return true
            }
            if (args.size < 2) {
                sender.sendMessage(Component.text("Usage: /disc play <disc_id>", NamedTextColor.RED))
                return true
            }
            val discId = args[1]
            val disc = plugin.discManager.getDisc(discId)
            if (disc == null) {
                sender.sendMessage(Component.text("Disc not found.", NamedTextColor.RED))
                return true
            }
            
            // Play the disc (no longer stopping ALL sounds - let user use /disc stop if needed)
            sender.playSound(net.kyori.adventure.sound.Sound.sound(
                net.kyori.adventure.key.Key.key("peyajcustomdisc:disc.$discId"),
                net.kyori.adventure.sound.Sound.Source.RECORD,
                1f, 1f
            ))
            sender.sendActionBar(Component.text("Now Playing: ${disc.name} - ${disc.author}", NamedTextColor.AQUA))
            return true
        }

        if (args[0].equals("stop", ignoreCase = true)) {
            if (sender !is Player) return true
            sender.stopSound(net.kyori.adventure.sound.SoundStop.source(net.kyori.adventure.sound.Sound.Source.RECORD))
            sender.sendActionBar(Component.text("Stopped Music", NamedTextColor.RED))
            return true
        }

        if (args[0].equals("catalog", ignoreCase = true)) {
            if (sender !is Player) {
                 sender.sendMessage(Component.text("Players only.", NamedTextColor.RED))
                 return true
            }
            plugin.discGUI.openCatalog(sender, 0)
            return true
        }
        
        // --- REGION MUSIC COMMAND ---
        if (args[0].equals("region", ignoreCase = true)) {
            if (args.size < 2) {
                sender.sendMessage(Component.text("Usage: /disc region <set|add|loop|stereo|remove|list>", NamedTextColor.RED))
                return true
            }
            
            val sub = args[1].lowercase()
            
            if (sub == "set" || sub == "add") {
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /disc region $sub <region_name> <disc_id> [loop: on|off] [stereo: on|off]", NamedTextColor.RED))
                    return true
                }
                val regionName = args[2].lowercase()
                var discId: String? = null
                var loop = true
                var stereo = true

                if (args.size == 3) {
                    // /disc region add <region> -> check if player holds a custom disc
                    if (sender is Player) {
                        discId = plugin.discManager.getDiscIdFromItem(sender.inventory.itemInMainHand)
                    }
                    if (discId == null) {
                        sender.sendMessage(Component.text("Usage: /disc region $sub <region_name> <disc_id> [loop] [stereo]", NamedTextColor.RED))
                        if (sender is Player) {
                            sender.sendMessage(Component.text("Tip: You can also hold a custom disc in hand when running /disc region $sub <region_name>", NamedTextColor.GRAY))
                        }
                        return true
                    }
                } else if (args.size == 4) {
                    val param = args[3].lowercase()
                    val isLoopKeyword = param in listOf("on", "off", "true", "false", "loop", "once", "yes", "no")
                    if (isLoopKeyword && sender is Player && plugin.discManager.isCustomDisc(sender.inventory.itemInMainHand)) {
                        discId = plugin.discManager.getDiscIdFromItem(sender.inventory.itemInMainHand)
                        loop = parseLoopValue(param)
                    } else {
                        discId = args[3]
                        loop = true
                    }
                } else if (args.size == 5) {
                    val param1 = args[3].lowercase()
                    val isLoopKeyword = param1 in listOf("on", "off", "true", "false", "loop", "once", "yes", "no")
                    if (isLoopKeyword && sender is Player && plugin.discManager.isCustomDisc(sender.inventory.itemInMainHand)) {
                        discId = plugin.discManager.getDiscIdFromItem(sender.inventory.itemInMainHand)
                        loop = parseLoopValue(param1)
                        stereo = parseBooleanValue(args[4])
                    } else {
                        discId = args[3]
                        loop = parseLoopValue(args[4])
                    }
                } else {
                    discId = args[3]
                    loop = parseLoopValue(args[4])
                    stereo = parseBooleanValue(args[5])
                }

                if (discId == null || plugin.discManager.getDisc(discId) == null) {
                    sender.sendMessage(Component.text("Disc '$discId' not found.", NamedTextColor.RED))
                    return true
                }
                
                // Save to config (structured format)
                plugin.config.set("region-music.$regionName.disc", discId)
                plugin.config.set("region-music.$regionName.loop", loop)
                plugin.config.set("region-music.$regionName.stereo", stereo)
                plugin.saveConfig()
                
                var needsPackRebuild = false
                if (stereo) {
                    if (plugin.regionMusicManager?.ensureStereoFileExists(discId) == true) {
                        needsPackRebuild = true
                    }
                }

                // Reload manager
                plugin.regionMusicManager?.reload()

                if (needsPackRebuild) {
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                        })
                    })
                }
                
                val loopStatus = if (loop) "ON (Repeating)" else "OFF (Plays Once)"
                val stereoStatus = if (stereo) "ON (Stereo)" else "OFF (Mono)"
                sender.sendMessage(Component.text("✔ Region '$regionName' now plays disc '$discId' [Loop: $loopStatus] [Stereo: $stereoStatus].", NamedTextColor.GREEN))
                return true
            }

            if (sub == "loop") {
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /disc region loop <region_name> [on|off|toggle]", NamedTextColor.RED))
                    return true
                }
                val regionName = args[2].lowercase()
                val section = plugin.config.getConfigurationSection("region-music")
                if (section == null || !section.contains(regionName)) {
                    sender.sendMessage(Component.text("No region music configured for '$regionName'.", NamedTextColor.RED))
                    return true
                }

                val currentDiscId = if (section.isConfigurationSection(regionName)) {
                    section.getString("$regionName.disc") ?: ""
                } else {
                    section.getString(regionName) ?: ""
                }

                val currentLoop = if (section.isConfigurationSection(regionName)) {
                    section.getBoolean("$regionName.loop", true)
                } else {
                    true
                }

                val currentStereo = if (section.isConfigurationSection(regionName)) {
                    section.getBoolean("$regionName.stereo", true)
                } else {
                    true
                }

                val newLoop = if (args.size >= 4) {
                    val param = args[3].lowercase()
                    if (param == "toggle") !currentLoop else parseLoopValue(param)
                } else {
                    !currentLoop // Default to toggle if no extra argument given
                }

                plugin.config.set("region-music.$regionName.disc", currentDiscId)
                plugin.config.set("region-music.$regionName.loop", newLoop)
                plugin.config.set("region-music.$regionName.stereo", currentStereo)
                plugin.saveConfig()

                plugin.regionMusicManager?.reload()

                val stateStr = if (newLoop) "ON (Repeating)" else "OFF (Plays Once)"
                sender.sendMessage(Component.text("✔ Looping for region '$regionName' set to $stateStr.", NamedTextColor.GREEN))
                return true
            }

            if (sub == "stereo") {
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /disc region stereo <region_name> [on|off|toggle]", NamedTextColor.RED))
                    return true
                }
                val regionName = args[2].lowercase()
                val section = plugin.config.getConfigurationSection("region-music")
                if (section == null || !section.contains(regionName)) {
                    sender.sendMessage(Component.text("No region music configured for '$regionName'.", NamedTextColor.RED))
                    return true
                }

                val currentDiscId = if (section.isConfigurationSection(regionName)) {
                    section.getString("$regionName.disc") ?: ""
                } else {
                    section.getString(regionName) ?: ""
                }

                val currentLoop = if (section.isConfigurationSection(regionName)) {
                    section.getBoolean("$regionName.loop", true)
                } else {
                    true
                }

                val currentStereo = if (section.isConfigurationSection(regionName)) {
                    section.getBoolean("$regionName.stereo", true)
                } else {
                    true
                }

                val newStereo = if (args.size >= 4) {
                    val param = args[3].lowercase()
                    if (param == "toggle") !currentStereo else parseBooleanValue(param)
                } else {
                    !currentStereo
                }

                plugin.config.set("region-music.$regionName.disc", currentDiscId)
                plugin.config.set("region-music.$regionName.loop", currentLoop)
                plugin.config.set("region-music.$regionName.stereo", newStereo)
                plugin.saveConfig()

                var needsPackRebuild = false
                if (newStereo && currentDiscId.isNotBlank()) {
                    if (plugin.regionMusicManager?.ensureStereoFileExists(currentDiscId) == true) {
                        needsPackRebuild = true
                    }
                }

                plugin.regionMusicManager?.reload()

                if (needsPackRebuild) {
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                        })
                    })
                }

                val stateStr = if (newStereo) "ON (2-Channel Background Audio)" else "OFF (Mono Spatial Audio)"
                sender.sendMessage(Component.text("✔ Stereo for region '$regionName' set to $stateStr.", NamedTextColor.GREEN))
                return true
            }
            
            if (sub == "remove" || sub == "delete") {
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /disc region remove <region_name>", NamedTextColor.RED))
                    return true
                }
                val regionName = args[2].lowercase()
                
                // Stop music for any players currently inside or tracked in this region
                plugin.regionMusicManager?.removeRegion(regionName)

                // Clean up config case-insensitively
                val section = plugin.config.getConfigurationSection("region-music")
                if (section != null) {
                    for (key in section.getKeys(false)) {
                        if (key.equals(regionName, ignoreCase = true)) {
                            section.set(key, null)
                        }
                    }
                }
                plugin.config.set("region-music.$regionName", null)
                plugin.saveConfig()
                
                plugin.regionMusicManager?.reload()
                
                sender.sendMessage(Component.text("✔ Region '$regionName' music removed.", NamedTextColor.YELLOW))
                return true
            }
            
            if (sub == "list") {
                val section = plugin.config.getConfigurationSection("region-music")
                if (section == null || section.getKeys(false).isEmpty()) {
                    sender.sendMessage(Component.text("No region-music mappings configured.", NamedTextColor.GRAY))
                    return true
                }
                
                sender.sendMessage(Component.text("Region Music Mappings", NamedTextColor.AQUA))
                section.getKeys(false).forEach { region ->
                    val discId = if (section.isConfigurationSection(region)) {
                        section.getString("$region.disc") ?: "?"
                    } else {
                        section.getString(region) ?: "?"
                    }
                    val isLoop = if (section.isConfigurationSection(region)) {
                        section.getBoolean("$region.loop", true)
                    } else {
                        true
                    }
                    val isStereo = if (section.isConfigurationSection(region)) {
                        section.getBoolean("$region.stereo", true)
                    } else {
                        true
                    }
                    val loopColor = if (isLoop) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY
                    val loopLabel = if (isLoop) "ON" else "OFF"
                    val stereoColor = if (isStereo) NamedTextColor.AQUA else NamedTextColor.DARK_GRAY
                    val stereoLabel = if (isStereo) "ON" else "OFF"

                    sender.sendMessage(
                        Component.text("  $region → $discId ", NamedTextColor.YELLOW)
                            .append(Component.text("[Loop: $loopLabel] ", loopColor))
                            .append(Component.text("[Stereo: $stereoLabel]", stereoColor))
                    )
                }
                return true
            }
            
            sender.sendMessage(Component.text("Unknown subcommand. Use: set, add, loop, stereo, remove, list", NamedTextColor.RED))
            return true
        }

        if (args[0].equals("add", ignoreCase = true)) {
            if (!sender.hasPermission("pjcustomdisc.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }
            if (args.size < 5) {
                sender.sendMessage(Component.text("Usage: /disc add <id> <url> <name> <author>", NamedTextColor.RED))
                return true
            }

            val rawId = args[1].lowercase()
            val discId = rawId.replace(Regex("[^a-z0-9]"), "_")
            val urlStr = args[2]
            val name = args[3].replace("_", " ")
            val author = args[4].replace("_", " ")
            val style = if (args.size > 5) args[5].lowercase() else "custom"

            // Check if disc exists
            if (plugin.discManager.getDisc(discId) != null) {
                sender.sendMessage(Component.text("Disc ID '$discId' already exists.", NamedTextColor.RED))
                return true
            }

            sender.sendMessage(Component.text("Downloading and converting disc audio in the background...", NamedTextColor.YELLOW))

            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                try {
                    val url = java.net.URL(urlStr)
                    val connection = url.openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 15000

                    val filename = url.path.substringAfterLast('/', "download.mp3").lowercase()
                    val isOgg = filename.endsWith(".ogg")
                    val validExtensions = listOf(".mp3", ".wav", ".flac", ".m4a", ".mp4", ".wma", ".aac", ".webm")
                    val isValidInput = isOgg || validExtensions.any { filename.endsWith(it) }

                    if (!isValidInput) {
                        sender.sendMessage(Component.text("Invalid format. Supported: .ogg (Direct), .mp3, .wav, .flac, .m4a, .mp4, .wma, .aac", NamedTextColor.RED))
                        return@Runnable
                    }

                    val targetOgg = java.io.File(plugin.dataFolder, "discs/$discId.ogg")
                    val targetStereoOgg = java.io.File(plugin.dataFolder, "discs/${discId}_stereo.ogg")
                    targetOgg.parentFile.mkdirs()
                    var durationSeconds = 0

                    if (!isOgg) {
                        if (!com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                            sender.sendMessage(Component.text("Conversion failed: FFmpeg not installed on server host OS.", NamedTextColor.RED))
                            return@Runnable
                        }

                        val ext = filename.substringAfterLast('.', "tmp")
                        val tempFile = java.io.File(plugin.dataFolder, "temp/$discId.$ext")
                        tempFile.parentFile.mkdirs()

                        connection.getInputStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val result = com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(tempFile, targetOgg, channels = 1)
                        com.peyaj.jukeboxweb.util.AudioConverter.convertToOggStereo(tempFile, targetStereoOgg)
                        tempFile.delete()

                        if (!result.first) {
                            sender.sendMessage(Component.text("Conversion Failed. Check server console.", NamedTextColor.RED))
                            return@Runnable
                        }
                        durationSeconds = result.second
                    } else {
                        connection.getInputStream().use { input ->
                            targetStereoOgg.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
                            com.peyaj.jukeboxweb.util.AudioConverter.convertToOgg(targetStereoOgg, targetOgg, channels = 1)
                        } else {
                            targetStereoOgg.copyTo(targetOgg, overwrite = true)
                        }
                        durationSeconds = com.peyaj.jukeboxweb.util.AudioConverter.getAudioDuration(targetOgg)
                    }

                    val disc = com.peyaj.jukeboxweb.disc.CustomDisc(
                        id = discId,
                        name = name,
                        author = author,
                        lore = emptyList(),
                        durationSeconds = durationSeconds,
                        style = style
                    )

                    // Sync back to main thread
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        plugin.discManager.addDisc(disc)
                        
                        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                            plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                                sender.sendMessage(Component.text("✔ Custom disc '$discId' added successfully! Resource pack rebuilt.", NamedTextColor.GREEN))
                            })
                        })
                    })
                } catch (e: Exception) {
                    sender.sendMessage(Component.text("Failed to add custom disc: ${e.message}", NamedTextColor.RED))
                    plugin.logger.warning("Failed to add custom disc via command: ${e.message}")
                }
            })

            return true
        }

        if (args[0].equals("delete", ignoreCase = true) || args[0].equals("remove", ignoreCase = true)) {
            if (!sender.hasPermission("pjcustomdisc.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }
            if (args.size < 2) {
                sender.sendMessage(Component.text("Usage: /disc delete <disc_id>", NamedTextColor.RED))
                return true
            }

            val discId = args[1]
            val deleted = plugin.discManager.deleteDisc(discId)
            
            if (deleted) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                        sender.sendMessage(Component.text("✔ Custom disc '$discId' deleted successfully! Resource pack rebuilt.", NamedTextColor.GREEN))
                    })
                })
            } else {
                sender.sendMessage(Component.text("Disc ID '$discId' not found.", NamedTextColor.RED))
            }
            return true
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission("pjcustomdisc.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }
            sender.sendMessage(Component.text("Reloading configuration...", NamedTextColor.YELLOW))
            try {
                plugin.reloadPlugin()
                sender.sendMessage(Component.text("Configuration and Web Server reloaded!", NamedTextColor.GREEN))
            } catch (e: Exception) {
                sender.sendMessage(Component.text("Failed to reload: ${e.message}", NamedTextColor.RED))
                e.printStackTrace()
            }
            return true
        }

        if (args[0].equals("checkupdate", ignoreCase = true) || args[0].equals("update", ignoreCase = true)) {
            if (!sender.hasPermission("pjcustomdisc.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }
            val isTest = args.size >= 2 && args[1].equals("test", ignoreCase = true)
            com.peyaj.jukeboxweb.update.UpdateChecker.handleCheckUpdateCommand(sender, plugin, isTest)
            return true
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("give", "list", "play", "stop", "web", "catalog", "region", "reload", "add", "delete", "remove", "checkupdate").filter { it.startsWith(args[0], true) }
        }
        if (args.size == 2 && (args[0].equals("checkupdate", ignoreCase = true) || args[0].equals("update", ignoreCase = true))) {
            return listOf("test").filter { it.startsWith(args[1], true) }
        }
        if (args.size == 2 && args[0].equals("give", ignoreCase = true)) {
            return plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }
        }
        if (args.size == 3 && args[0].equals("give", ignoreCase = true)) {
            return plugin.discManager.getAllDiscs().map { it.id }.filter { it.startsWith(args[2], true) }
        }
        if (args.size == 2 && args[0].equals("play", ignoreCase = true)) {
             return plugin.discManager.getAllDiscs().map { it.id }.filter { it.startsWith(args[1], true) }
        }
        // Add command tab completion
        if (args[0].equals("add", ignoreCase = true)) {
            if (args.size == 2) return listOf("[id]")
            if (args.size == 3) return listOf("[url]")
            if (args.size == 4) return listOf("[name_with_underscores]")
            if (args.size == 5) return listOf("[author_with_underscores]")
            if (args.size == 6) return listOf("cat", "13", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait", "otherside", "5", "pigstep", "relic", "creator", "precipice", "creator_music_box", "tears", "lava_chicken").filter { it.startsWith(args[5], true) }
        }

        // Region tab completion
        if (args[0].equals("region", ignoreCase = true)) {
            if (args.size == 2) {
                return listOf("set", "add", "loop", "stereo", "remove", "list").filter { it.startsWith(args[1], true) }
            }
            if (args.size == 3 && (args[1].equals("set", ignoreCase = true) || args[1].equals("add", ignoreCase = true))) {
                // Suggest WorldGuard regions from player's current world
                if (sender is Player) {
                    return getWorldGuardRegions(sender).filter { it.startsWith(args[2], true) }
                }
                return emptyList()
            }
            if (args.size == 3 && (args[1].equals("remove", ignoreCase = true) || args[1].equals("delete", ignoreCase = true) || args[1].equals("loop", ignoreCase = true) || args[1].equals("stereo", ignoreCase = true))) {
                // Suggest existing region mappings
                val section = plugin.config.getConfigurationSection("region-music")
                return section?.getKeys(false)?.filter { it.startsWith(args[2], true) }?.toList() ?: emptyList()
            }
            if (args.size == 4 && (args[1].equals("set", ignoreCase = true) || args[1].equals("add", ignoreCase = true))) {
                val suggestions = mutableListOf<String>()
                suggestions.addAll(plugin.discManager.getAllDiscs().map { it.id })
                if (sender is Player && plugin.discManager.isCustomDisc(sender.inventory.itemInMainHand)) {
                    suggestions.addAll(listOf("on", "off", "true", "false"))
                }
                return suggestions.filter { it.startsWith(args[3], true) }
            }
            if (args.size == 4 && (args[1].equals("loop", ignoreCase = true) || args[1].equals("stereo", ignoreCase = true))) {
                return listOf("on", "off", "toggle", "true", "false").filter { it.startsWith(args[3], true) }
            }
            if (args.size == 5 && (args[1].equals("set", ignoreCase = true) || args[1].equals("add", ignoreCase = true))) {
                return listOf("on", "off", "true", "false").filter { it.startsWith(args[4], true) }
            }
            if (args.size == 6 && (args[1].equals("set", ignoreCase = true) || args[1].equals("add", ignoreCase = true))) {
                return listOf("on", "off", "true", "false").filter { it.startsWith(args[5], true) }
            }
        }
        // Delete/remove command tab completion
        if (args[0].equals("delete", ignoreCase = true) || args[0].equals("remove", ignoreCase = true)) {
            if (args.size == 2) {
                return plugin.discManager.getAllDiscs().map { it.id }.filter { it.startsWith(args[1], true) }
            }
        }

        return emptyList()
    }
    
    private fun getWorldGuardRegions(player: Player): List<String> {
        return try {
            val container = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer
            val worldName = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.world)
            val manager = container.get(worldName) ?: return emptyList()
            manager.regions.keys.toList()
        } catch (e: Exception) {
            // WorldGuard not available
            emptyList()
        }
    }

    private fun parseLoopValue(input: String?): Boolean {
        if (input == null) return true
        return when (input.lowercase()) {
            "false", "off", "no", "once", "none", "disable", "disabled", "0" -> false
            else -> true
        }
    }

    private fun parseBooleanValue(input: String?): Boolean {
        if (input == null) return true
        return when (input.lowercase()) {
            "false", "off", "no", "mono", "disable", "disabled", "0" -> false
            else -> true
        }
    }
}


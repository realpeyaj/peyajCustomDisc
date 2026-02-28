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
            sender.sendMessage(Component.text("--- peyajCustomDisc Help ---", NamedTextColor.AQUA))
            sender.sendMessage(Component.text("/disc give <player> <id> - Give a custom disc", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc list - List all available discs", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc play <id> - Play a disc everywhere (Portable)", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc stop - Stop all disc music", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region set <region> <disc> - Set region music", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region remove <region> - Remove region music", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("/disc region list - List region mappings", NamedTextColor.YELLOW))
            if (sender.hasPermission("pjcustomdisc.admin")) {
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
            
            val message = Component.text("Click here to login to Disc Creator", NamedTextColor.GREEN)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(loginLink, NamedTextColor.GRAY)))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(loginLink))
            
            sender.sendMessage(Component.text("----------------------------------", NamedTextColor.DARK_GRAY))
            sender.sendMessage(message)
            sender.sendMessage(Component.text("Reference: This link is one-time use.", NamedTextColor.GRAY))
            sender.sendMessage(Component.text("----------------------------------", NamedTextColor.DARK_GRAY))
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
                sender.sendMessage(Component.text("Usage: /disc region <set|remove|list>", NamedTextColor.RED))
                return true
            }
            
            val sub = args[1].lowercase()
            
            if (sub == "set") {
                if (args.size < 4) {
                    sender.sendMessage(Component.text("Usage: /disc region set <region_name> <disc_id>", NamedTextColor.RED))
                    return true
                }
                val regionName = args[2].lowercase()
                val discId = args[3]
                
                // Verify disc exists
                if (plugin.discManager.getDisc(discId) == null) {
                    sender.sendMessage(Component.text("Disc '$discId' not found.", NamedTextColor.RED))
                    return true
                }
                
                // Save to config
                plugin.config.set("region-music.$regionName", discId)
                plugin.saveConfig()
                
                // Reload manager
                plugin.regionMusicManager?.reload()
                
                sender.sendMessage(Component.text("✔ Region '$regionName' now plays disc '$discId'.", NamedTextColor.GREEN))
                return true
            }
            
            if (sub == "remove") {
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /disc region remove <region_name>", NamedTextColor.RED))
                    return true
                }
                val regionName = args[2].lowercase()
                
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
                
                sender.sendMessage(Component.text("--- Region Music Mappings ---", NamedTextColor.AQUA))
                section.getKeys(false).forEach { region ->
                    val discId = section.getString(region) ?: "?"
                    sender.sendMessage(Component.text("  $region → $discId", NamedTextColor.YELLOW))
                }
                return true
            }
            
            sender.sendMessage(Component.text("Unknown subcommand. Use: set, remove, list", NamedTextColor.RED))
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

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("give", "list", "play", "stop", "web", "catalog", "region", "reload").filter { it.startsWith(args[0], true) }
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
        // Region tab completion
        if (args[0].equals("region", ignoreCase = true)) {
            if (args.size == 2) {
                return listOf("set", "remove", "list").filter { it.startsWith(args[1], true) }
            }
            if (args.size == 3 && args[1].equals("set", ignoreCase = true)) {
                // Suggest WorldGuard regions from player's current world
                if (sender is Player) {
                    return getWorldGuardRegions(sender).filter { it.startsWith(args[2], true) }
                }
                return emptyList()
            }
            if (args.size == 3 && args[1].equals("remove", ignoreCase = true)) {
                // Suggest existing region mappings
                val section = plugin.config.getConfigurationSection("region-music")
                return section?.getKeys(false)?.filter { it.startsWith(args[2], true) }?.toList() ?: emptyList()
            }
            if (args.size == 4 && args[1].equals("set", ignoreCase = true)) {
                // Suggest disc IDs
                return plugin.discManager.getAllDiscs().map { it.id }.filter { it.startsWith(args[3], true) }
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
}


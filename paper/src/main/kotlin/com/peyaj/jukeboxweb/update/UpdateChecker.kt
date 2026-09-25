package com.peyaj.jukeboxweb.update

import com.google.gson.JsonParser
import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker : Listener {

    private const val MODRINTH_API_URL = "https://api.modrinth.com/v2/project/peyajcustomdisc/version"
    private const val MODRINTH_PAGE_URL = "https://modrinth.com/plugin/peyajcustomdisc"

    @Volatile
    var updateAvailable: Boolean = false
        private set

    @Volatile
    var cachedLatestVersion: String? = null
        private set

    @Volatile
    var currentVersion: String = ""
        private set

    fun init(plugin: PeyajCustomDisc) {
        currentVersion = plugin.description.version
        plugin.server.pluginManager.registerEvents(this, plugin)

        if (plugin.config.getBoolean("check-updates", true)) {
            checkForUpdates(plugin, notifyConsole = true)
        }
    }

    fun checkForUpdates(plugin: PeyajCustomDisc, notifyConsole: Boolean = false) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val latestVerNumber = fetchLatestVersion()
            if (latestVerNumber != null) {
                cachedLatestVersion = latestVerNumber
                if (isNewerVersion(currentVersion, latestVerNumber)) {
                    updateAvailable = true
                    if (notifyConsole) {
                        plugin.logger.warning("There is a newer plugin version available: $latestVerNumber, you're on: $currentVersion. Download it at: $MODRINTH_PAGE_URL")
                    }
                } else {
                    updateAvailable = false
                }
            }
        })
    }

    fun fetchLatestVersion(): String? {
        return try {
            val url = URL(MODRINTH_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "peyajCustomDisc/${currentVersion} (peyaj)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JsonParser.parseString(json).asJsonArray

                for (elem in array) {
                    val obj = elem.asJsonObject
                    val loadersArray = obj.getAsJsonArray("loaders")
                    val loaders = loadersArray?.map { it.asString.lowercase() } ?: emptyList()
                    if (loaders.contains("paper") || loaders.contains("purpur") || loaders.contains("spigot")) {
                        return obj.get("version_number")?.asString
                    }
                }
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            null
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!player.isOp && !player.hasPermission("pjcustomdisc.admin")) return
        if (!updateAvailable) return

        val latest = cachedLatestVersion ?: return

        // Delay 20 ticks (1 second) so it appears cleanly after join messages
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.Bukkit.getPluginManager().getPlugin("peyajCustomDisc") ?: return,
            Runnable {
                if (player.isOnline && updateAvailable) {
                    sendUpdateNotification(player, currentVersion, latest)
                }
            },
            20L
        )
    }

    fun sendUpdateNotification(player: Player, current: String, latest: String) {
        val message = Component.text("There is a newer plugin version available: ", NamedTextColor.YELLOW)
            .append(Component.text(latest, NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD))
            .append(Component.text(", you're on: ", NamedTextColor.YELLOW))
            .append(Component.text(current, NamedTextColor.RED))
            .append(Component.text("\nDownload it at: ", NamedTextColor.GRAY))
            .append(
                Component.text(MODRINTH_PAGE_URL, NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(MODRINTH_PAGE_URL))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to open Modrinth", NamedTextColor.YELLOW)))
            )

        player.sendMessage(message)
    }

    fun handleCheckUpdateCommand(sender: org.bukkit.command.CommandSender, plugin: PeyajCustomDisc, forceTest: Boolean = false) {
        if (forceTest) {
            val dummyLatest = "2.5.0"
            sender.sendMessage(
                Component.text("There is a newer plugin version available: ", NamedTextColor.YELLOW)
                    .append(Component.text(dummyLatest, NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD))
                    .append(Component.text(", you're on: ", NamedTextColor.YELLOW))
                    .append(Component.text(currentVersion, NamedTextColor.RED))
                    .append(Component.text("\nDownload it at: ", NamedTextColor.GRAY))
                    .append(
                        Component.text(MODRINTH_PAGE_URL, NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(MODRINTH_PAGE_URL))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to open Modrinth", NamedTextColor.YELLOW)))
                    )
            )
            return
        }

        sender.sendMessage(Component.text("Checking Modrinth for updates...", NamedTextColor.YELLOW))
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val latest = fetchLatestVersion()
            if (latest != null) {
                cachedLatestVersion = latest
                if (isNewerVersion(currentVersion, latest)) {
                    updateAvailable = true
                    sender.sendMessage(
                        Component.text("There is a newer plugin version available: ", NamedTextColor.YELLOW)
                            .append(Component.text(latest, NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD))
                            .append(Component.text(", you're on: ", NamedTextColor.YELLOW))
                            .append(Component.text(currentVersion, NamedTextColor.RED))
                            .append(Component.text("\nDownload it at: ", NamedTextColor.GRAY))
                            .append(
                                Component.text(MODRINTH_PAGE_URL, NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                                    .clickEvent(ClickEvent.openUrl(MODRINTH_PAGE_URL))
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to open Modrinth", NamedTextColor.YELLOW)))
                            )
                    )
                } else {
                    updateAvailable = false
                    sender.sendMessage(Component.text("You are running the latest version (v$currentVersion). Latest on Modrinth: v$latest.", NamedTextColor.GREEN))
                }
            } else {
                sender.sendMessage(Component.text("Could not reach Modrinth API to check for updates.", NamedTextColor.RED))
            }
        })
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current.equals(latest, ignoreCase = true)) return false

        val cleanCurrent = current.split("-")[0]
        val cleanLatest = latest.split("-")[0]
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        return latest != current && !current.contains("SNAPSHOT", ignoreCase = true)
    }
}


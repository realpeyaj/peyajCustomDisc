package com.peyaj.jukeboxweb

import com.peyaj.jukeboxweb.disc.PaperDiscManager
import com.peyaj.jukeboxweb.pack.PackGenerator
import com.peyaj.jukeboxweb.platform.PaperPlatformAdapter
import com.peyaj.jukeboxweb.util.FFmpegManager
import com.peyaj.jukeboxweb.web.WebServer
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

import com.peyaj.jukeboxweb.listener.JukeboxListener
import com.peyaj.jukeboxweb.command.JukeboxCommand
import com.peyaj.jukeboxweb.api.PeyajDiscAPI
import com.peyaj.jukeboxweb.api.PeyajDiscAPIImpl

class PeyajCustomDisc : JavaPlugin() {

    companion object {
        lateinit var instance: PeyajCustomDisc
            private set
        
        val logger: Logger
            get() = instance.logger
    }

    lateinit var platform: PaperPlatformAdapter
    lateinit var discManager: PaperDiscManager
    lateinit var packGenerator: PackGenerator
    var webServer: WebServer? = null
    var jukeboxListener: JukeboxListener? = null
    lateinit var discGUI: com.peyaj.jukeboxweb.gui.DiscGUI
    var regionMusicManager: com.peyaj.jukeboxweb.region.RegionMusicManager? = null

    override fun onEnable() {
        instance = this
        printStartupLogo()
        
        // Save default config & auto-migrate new keys from newer plugin versions
        saveDefaultConfig()
        updateConfig()
        
        // Check public-url warning
        val publicUrl = config.getString("public-url", "http://localhost:8080") ?: "http://localhost:8080"
        if (publicUrl.contains("localhost") || publicUrl.contains("127.0.0.1")) {
            logger.warning("[!] 'public-url' in config.yml is set to '$publicUrl'. Players connecting over the internet won't be able to download the resource pack unless this is changed to your server's public IP or domain!")
        }

        // Initialize Platform & Managers
        platform = PaperPlatformAdapter(this)
        discManager = PaperDiscManager(this)
        discManager.loadDiscs()
        
        val range = config.getDouble("jukebox.range", 64.0)
        packGenerator = PackGenerator(
            dataFolder = dataFolder,
            jukeboxRange = range,
            logInfo = { logger.info(it) },
            logWarning = { logger.warning(it) },
            logSevere = { logger.severe(it) }
        )
        
        // Ensure FFmpeg (Auto Download if needed)
        FFmpegManager.ensureFFmpeg(
            dataFolder = dataFolder,
            logInfo = { logger.info(it) },
            logWarning = { logger.warning(it) },
            logSevere = { msg, err -> logger.severe(msg); err?.printStackTrace() }
        )

        try {
            packGenerator.buildResourcePack(discManager.getAllDiscs())
            com.peyaj.jukeboxweb.pack.PackUpdater.updateGeyserPack(this)
        } catch (e: Exception) {
            logger.warning("Failed to build/deploy initial resource pack: ${e.message}")
        }

        // Register Commands
        getCommand("disc")?.setExecutor(JukeboxCommand(this))
        
        // Register Listeners
        jukeboxListener = JukeboxListener(this)
        server.pluginManager.registerEvents(jukeboxListener!!, this)
        server.pluginManager.registerEvents(com.peyaj.jukeboxweb.pack.PackUpdater, this)
        
        discGUI = com.peyaj.jukeboxweb.gui.DiscGUI(this)
        server.pluginManager.registerEvents(discGUI, this)
        
        // WorldGuard Integration (Optional)
        if (server.pluginManager.getPlugin("WorldGuard") != null) {
            regionMusicManager = com.peyaj.jukeboxweb.region.RegionMusicManager(this)
            server.pluginManager.registerEvents(regionMusicManager!!, this)
            logger.info("WorldGuard detected! Region music enabled.")
        } else {
            logger.info("WorldGuard not found. Region music disabled.")
        }
        
        // Register API
        server.servicesManager.register(
            PeyajDiscAPI::class.java,
            PeyajDiscAPIImpl(this),
            this,
            org.bukkit.plugin.ServicePriority.Normal
        )
        logger.info("PeyajDiscAPI registered for developers!")
        
        // bStats Metrics
        try {
            org.bstats.bukkit.Metrics(this, 32671)
            logger.info("bStats metrics enabled.")
        } catch (e: Exception) {
            logger.warning("Failed to initialize bStats: ${e.message}")
        }
        
        // Initialize WebServer
        webServer = WebServer(platform, discManager, packGenerator)
        val port = config.getInt("web-port", 8080)
        
        Thread {
            try {
                webServer?.start(port)
                logger.info("Web interface running on port $port")
            } catch (e: Exception) {
                logger.severe("Failed to start web server: ${e.message}")
            }
        }.start()

        // Initialize UpdateChecker (Modrinth)
        com.peyaj.jukeboxweb.update.UpdateChecker.init(this)

        logger.info("peyajCustomDisc enabled!")
    }

    private fun updateConfig() {
        try {
            val configFile = java.io.File(dataFolder, "config.yml")
            if (!configFile.exists()) return

            val content = configFile.readText(java.nio.charset.StandardCharsets.UTF_8)
            val additions = StringBuilder()

            if (!content.contains("check-updates:")) {
                additions.append("\n# Should the plugin check for new releases on Modrinth and notify OPs upon login?\ncheck-updates: true\n")
            }

            if (additions.isNotEmpty()) {
                configFile.appendText(additions.toString(), java.nio.charset.StandardCharsets.UTF_8)
                reloadConfig()
                logger.info("✔ Automatically updated config.yml with new settings from this version.")
            }

            // Auto-migrate auto-eject option if missing from existing config
            val refreshedContent = configFile.readText(java.nio.charset.StandardCharsets.UTF_8)
            if (!refreshedContent.contains("auto-eject:")) {
                if (refreshedContent.contains("particle-type:")) {
                    val migrated = refreshedContent.replace(
                        Regex("(particle-type:.*)", RegexOption.MULTILINE),
                        "$1\n  # Whether jukeboxes automatically eject the disc onto the block when the track ends (if not looping)\n  auto-eject: true"
                    )
                    configFile.writeText(migrated, java.nio.charset.StandardCharsets.UTF_8)
                    reloadConfig()
                    logger.info("✔ Automatically updated config.yml with auto-eject setting.")
                }
            }
        } catch (e: Exception) {
            logger.warning("Could not auto-migrate config.yml: ${e.message}")
        }
    }

    override fun onDisable() {
        webServer?.stop()
        jukeboxListener?.disable()
        regionMusicManager?.disable()
        logger.info("peyajCustomDisc disabled!")
    }
    
    fun reloadPlugin() {
        reloadConfig()
        
        // Reload Region Music
        regionMusicManager?.reload()
        packGenerator.jukeboxRange = config.getDouble("jukebox.range", 64.0)

        // Force rebuild resource pack and update all connected players
        server.scheduler.runTaskAsynchronously(this, Runnable {
            try {
                packGenerator.buildResourcePack(discManager.getAllDiscs(), force = true)
                server.scheduler.runTask(this, Runnable {
                    com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(this)
                })
            } catch (e: Exception) {
                logger.warning("Failed to rebuild resource pack during reload: ${e.message}")
            }
        })

        // Check for updates
        if (config.getBoolean("check-updates", true)) {
            com.peyaj.jukeboxweb.update.UpdateChecker.checkForUpdates(this, notifyConsole = false)
        }
        
        // Restart Web Server
        val port = config.getInt("web-port", 8080)
        webServer?.stop()
        
        webServer = WebServer(platform, discManager, packGenerator)
        
        Thread {
            try {
                webServer?.start(port)
                logger.info("Web interface restarted on port $port")
            } catch (e: Exception) {
                logger.severe("Failed to restart web server: ${e.message}")
            }
        }.start()
        
        logger.info("Configuration reloaded!")
    }

    private fun printStartupLogo() {
        val logo = """
§b                            _   §3 ____  ____  
§b  _ __   ___  _   _  __ _  (_) §3 / ___||  _ \ 
§b | '_ \ / _ \| | | |/ _` | | |§3 | |    | | | |
§b | |_) |  __/| |_| | (_| | | |§3 | |___ | |_| |
§b | .__/ \___| \__, |\__,_| | |§3  \____||____/ 
§b |_|          |___/       _/ |              
§b                         |__/               
      §fpeyajCustomDisc §7v${description.version} §8| §fMade by §bpeyaj
        """.trimIndent()
        
        logo.lines().forEach { server.consoleSender.sendMessage(it) }
    }
}

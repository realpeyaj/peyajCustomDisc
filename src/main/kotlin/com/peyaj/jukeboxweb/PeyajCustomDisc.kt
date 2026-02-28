package com.peyaj.jukeboxweb

import com.peyaj.jukeboxweb.disc.DiscManager
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

    lateinit var discManager: DiscManager
    lateinit var packGenerator: com.peyaj.jukeboxweb.pack.PackGenerator
    var webServer: WebServer? = null
    var jukeboxListener: JukeboxListener? = null
    lateinit var discGUI: com.peyaj.jukeboxweb.gui.DiscGUI
    var regionMusicManager: com.peyaj.jukeboxweb.region.RegionMusicManager? = null

    override fun onEnable() {
        instance = this
        printStartupLogo()
        
        // Save default config
        saveDefaultConfig()
        
        // Initialize Managers
        discManager = DiscManager(this)
        discManager.loadDiscs()
        
        packGenerator = com.peyaj.jukeboxweb.pack.PackGenerator(this)
        
        // Ensure FFmpeg (Auto Download if needed)
        com.peyaj.jukeboxweb.util.FFmpegManager.ensureFFmpeg(this)

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
        
        // Initialize WebServer
        webServer = WebServer(this)
        val port = config.getInt("web-port", 8080)
        
        Thread {
            try {
                webServer?.start(port)
                logger.info("Web interface running on port $port")
            } catch (e: Exception) {
                logger.severe("Failed to start web server: ${e.message}")
            }
        }.start()

        logger.info("peyajCustomDisc enabled!")
    }

    override fun onDisable() {
        webServer?.stop()
        jukeboxListener?.disable()
        logger.info("peyajCustomDisc disabled!")
    }
    
    fun reloadPlugin() {
        reloadConfig()
        
        // Reload Region Music
        regionMusicManager?.reload()
        
        // Restart Web Server
        val port = config.getInt("web-port", 8080)
        webServer?.stop()
        
        webServer = WebServer(this)
        
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
§b                      _      §3  ____  ____  
§b  _ __   ___  _   _  __ _  (_) §3 / ___||  _ \ 
§b | '_ \ / _ \| | | |/ _` | | |§3 | |    | | | |
§b | |_) |  __/| |_| | (_| | | |§3 | |___ | |_| |
§b | .__/ \___| \__, |\__,_| | |§3  \____||____/ 
§b |_|          |___/       _/ |              
§b                         |__/               
      §fpeyajCustomDisc §7v1.5 §8| §fMade by §bpeyaj
        """.trimIndent()
        
        logo.lines().forEach { server.consoleSender.sendMessage(it) }
    }
}

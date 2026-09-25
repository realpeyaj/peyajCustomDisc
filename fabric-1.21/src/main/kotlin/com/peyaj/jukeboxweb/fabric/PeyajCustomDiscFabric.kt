package com.peyaj.jukeboxweb.fabric

import com.peyaj.jukeboxweb.fabric.command.FabricJukeboxCommand
import com.peyaj.jukeboxweb.fabric.config.FabricConfig
import com.peyaj.jukeboxweb.fabric.disc.FabricDiscManager
import com.peyaj.jukeboxweb.fabric.geyser.FabricGeyserHandler
import com.peyaj.jukeboxweb.fabric.listener.FabricJukeboxListener
import com.peyaj.jukeboxweb.fabric.pack.FabricPackUpdater
import com.peyaj.jukeboxweb.fabric.platform.FabricPlatformAdapter
import com.peyaj.jukeboxweb.pack.PackGenerator
import com.peyaj.jukeboxweb.util.FFmpegManager
import com.peyaj.jukeboxweb.web.WebServer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class PeyajCustomDiscFabric : ModInitializer {

    companion object {
        const val MOD_ID = "peyajcustomdisc"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var instance: PeyajCustomDiscFabric
            private set

        lateinit var configFile: File
        lateinit var config: FabricConfig
        lateinit var platform: FabricPlatformAdapter
        lateinit var discManager: FabricDiscManager
        lateinit var packGenerator: PackGenerator
        var webServer: WebServer? = null
        var server: MinecraftServer? = null

        fun reload() {
            config = FabricConfig.load(configFile)
            packGenerator.jukeboxRange = config.jukeboxRange

            webServer?.stop()
            webServer = WebServer(platform, discManager, packGenerator)

            val port = config.webPort
            Thread {
                try {
                    webServer?.start(port)
                    logger.info("Web interface restarted on port $port")
                } catch (e: Exception) {
                    logger.error("Failed to restart web server: ${e.message}")
                }
            }.start()

            logger.info("Configuration and WebServer reloaded!")
        }
    }

    override fun onInitialize() {
        instance = this
        printStartupLogo()

        val dataFolder = FabricLoader.getInstance().configDir.resolve("peyajCustomDisc").toFile()
        if (!dataFolder.exists()) dataFolder.mkdirs()

        configFile = File(dataFolder, "config.json")
        config = FabricConfig.load(configFile)

        val publicUrl = config.publicUrl
        if (publicUrl.contains("localhost") || publicUrl.contains("127.0.0.1")) {
            logger.warn("[!] 'publicUrl' in config.json is set to '$publicUrl'. Players connecting over the internet won't be able to download the resource pack unless this is changed to your server's public IP or domain!")
        }

        // Initialize Managers
        platform = FabricPlatformAdapter(dataFolder) { server }
        discManager = FabricDiscManager(
            dataFolder = dataFolder,
            logInfo = { logger.info(it) },
            logSevere = { msg, err -> logger.error(msg, err) }
        )
        discManager.loadDiscs()

        packGenerator = PackGenerator(
            dataFolder = dataFolder,
            jukeboxRange = config.jukeboxRange,
            logInfo = { logger.info(it) },
            logWarning = { logger.warn(it) },
            logSevere = { logger.error(it) }
        )

        // Ensure FFmpeg
        FFmpegManager.ensureFFmpeg(
            dataFolder = dataFolder,
            logInfo = { logger.info(it) },
            logWarning = { logger.warn(it) },
            logSevere = { msg, err -> logger.error(msg, err) }
        )

        try {
            packGenerator.buildResourcePack(discManager.getAllDiscs())
            FabricPackUpdater.updateGeyserPack()
        } catch (e: Exception) {
            logger.warn("Failed to build initial resource pack: ${e.message}")
        }

        // Register gameplay handlers & commands
        FabricJukeboxListener.register()
        FabricPackUpdater.register()
        FabricJukeboxCommand.register()

        // Server lifecycle hooks
        ServerLifecycleEvents.SERVER_STARTING.register { s ->
            server = s
        }
        ServerLifecycleEvents.SERVER_STARTED.register { s ->
            server = s
            FabricGeyserHandler.init()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            webServer?.stop()
            server = null
            logger.info("peyajCustomDisc stopped.")
        }

        // Start WebServer
        webServer = WebServer(platform, discManager, packGenerator)
        val port = config.webPort

        Thread {
            try {
                webServer?.start(port)
                logger.info("Web interface running on port $port")
            } catch (e: Exception) {
                logger.error("Failed to start web server: ${e.message}")
            }
        }.start()

        logger.info("peyajCustomDisc (Fabric) initialized!")
    }


    private fun printStartupLogo() {
        val logo = """
                             _    ____  ____  
   _ __   ___  _   _  __ _  (_)  / ___||  _ \ 
  | '_ \ / _ \| | | |/ _` | | | | |    | | | |
  | |_) |  __/| |_| | (_| | | | | |___ | |_| |
  | .__/ \___| \__, |\__,_| | |  \____||____/ 
  |_|          |___/       _/ |              
                          |__/               
       peyajCustomDisc Fabric | Made by peyaj
        """.trimIndent()
        logo.lines().forEach { logger.info(it) }
    }
}

package com.peyaj.jukeboxweb.neoforge

import com.peyaj.jukeboxweb.neoforge.command.NeoForgeJukeboxCommand
import com.peyaj.jukeboxweb.neoforge.config.NeoForgeConfig
import com.peyaj.jukeboxweb.neoforge.disc.NeoForgeDiscManager
import com.peyaj.jukeboxweb.neoforge.geyser.NeoForgeGeyserHandler
import com.peyaj.jukeboxweb.neoforge.listener.NeoForgeJukeboxListener
import com.peyaj.jukeboxweb.neoforge.pack.NeoForgePackUpdater
import com.peyaj.jukeboxweb.neoforge.platform.NeoForgePlatformAdapter
import com.peyaj.jukeboxweb.pack.PackGenerator
import com.peyaj.jukeboxweb.util.FFmpegManager
import com.peyaj.jukeboxweb.web.WebServer
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

@Mod(PeyajCustomDiscNeoForge.MOD_ID)
class PeyajCustomDiscNeoForge(modEventBus: IEventBus) {

    companion object {
        const val MOD_ID = "peyajcustomdisc"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var instance: PeyajCustomDiscNeoForge
            private set

        lateinit var configFile: File
        lateinit var config: NeoForgeConfig
        lateinit var platform: NeoForgePlatformAdapter
        lateinit var discManager: NeoForgeDiscManager
        lateinit var packGenerator: PackGenerator
        var webServer: WebServer? = null
        var server: MinecraftServer? = null

        fun startWebServer() {
            stopWebServer()
            val instance = WebServer(platform, discManager, packGenerator)
            webServer = instance
            val port = config.webPort
            Thread({
                try {
                    instance.start(port)
                    logger.info("Web interface running on port $port")
                } catch (e: Exception) {
                    logger.error("Failed to start web server: ${e.message}")
                }
            }, "peyajCustomDisc-WebServer").apply {
                isDaemon = true
                start()
            }
        }

        fun stopWebServer() {
            try {
                webServer?.stop()
            } catch (e: Exception) {
                logger.warn("Error stopping web server: ${e.message}")
            } finally {
                webServer = null
            }
        }

        fun reload() {
            config = NeoForgeConfig.load(configFile)
            packGenerator.jukeboxRange = config.jukeboxRange

            if (server != null) {
                startWebServer()
            }
            logger.info("Configuration and WebServer reloaded!")
        }
    }

    init {
        instance = this
        printStartupLogo()

        val dataFolder = FMLPaths.CONFIGDIR.get().resolve("peyajCustomDisc").toFile()
        if (!dataFolder.exists()) dataFolder.mkdirs()

        configFile = File(dataFolder, "config.json")
        config = NeoForgeConfig.load(configFile)

        val publicUrl = config.publicUrl
        if (publicUrl.contains("localhost") || publicUrl.contains("127.0.0.1")) {
            logger.warn("[!] 'publicUrl' in config.json is set to '$publicUrl'. Players connecting over the internet won't be able to download the resource pack unless this is changed to your server's public IP or domain!")
        }

        platform = NeoForgePlatformAdapter(dataFolder) { server }
        discManager = NeoForgeDiscManager(
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

        FFmpegManager.ensureFFmpeg(
            dataFolder = dataFolder,
            logInfo = { logger.info(it) },
            logWarning = { logger.warn(it) },
            logSevere = { msg, err -> logger.error(msg, err) }
        )

        try {
            packGenerator.buildResourcePack(discManager.getAllDiscs())
            NeoForgePackUpdater.updateGeyserPack()
        } catch (e: Exception) {
            logger.warn("Failed to build initial resource pack: ${e.message}")
        }

        NeoForgeJukeboxListener.register()
        NeoForgePackUpdater.register()
        NeoForgeJukeboxCommand.register()

        NeoForge.EVENT_BUS.addListener(ServerStartingEvent::class.java) { event ->
            server = event.server
            startWebServer()
        }
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent::class.java) { event ->
            server = event.server
            NeoForgeGeyserHandler.init()
        }
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent::class.java) {
            NeoForgeJukeboxListener.clearSessions()
            stopWebServer()
            server = null
            logger.info("peyajCustomDisc stopped.")
        }

        logger.info("peyajCustomDisc (NeoForge) initialized!")
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
       peyajCustomDisc NeoForge | Made by peyaj
        """.trimIndent()
        logo.lines().forEach { logger.info(it) }
    }
}

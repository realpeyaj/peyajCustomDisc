package com.peyaj.jukeboxweb.neoforge.platform

import com.peyaj.jukeboxweb.neoforge.PeyajCustomDiscNeoForge
import com.peyaj.jukeboxweb.neoforge.pack.NeoForgePackUpdater
import com.peyaj.jukeboxweb.platform.JukeboxPlatform
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture

class NeoForgePlatformAdapter(
    override val dataFolder: File,
    private val serverSupplier: () -> MinecraftServer?
) : JukeboxPlatform {

    private val logger = LoggerFactory.getLogger("peyajCustomDisc")

    override val haProxySupport: Boolean
        get() = PeyajCustomDiscNeoForge.config.haProxySupport

    override val adminPassword: String
        get() = PeyajCustomDiscNeoForge.config.adminPassword

    override val autoReloadGeyser: Boolean
        get() = PeyajCustomDiscNeoForge.config.autoReloadGeyser

    override fun runSync(task: Runnable) {
        val server = serverSupplier()
        if (server != null) {
            server.execute(task)
        } else {
            task.run()
        }
    }

    override fun runAsync(task: Runnable) {
        CompletableFuture.runAsync(task)
    }

    override fun onPackUpdated() {
        val server = serverSupplier() ?: return
        NeoForgePackUpdater.updateAllPlayers(server)
    }

    override fun dispatchCommand(command: String) {
        val server = serverSupplier() ?: return
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
    }

    override fun logInfo(message: String) {
        logger.info(message)
    }

    override fun logWarning(message: String) {
        logger.warn(message)
    }

    override fun logSevere(message: String) {
        logger.error(message)
    }
}

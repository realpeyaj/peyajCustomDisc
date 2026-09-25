package com.peyaj.jukeboxweb.fabric.platform

import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import com.peyaj.jukeboxweb.fabric.pack.FabricPackUpdater
import com.peyaj.jukeboxweb.platform.JukeboxPlatform
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture

class FabricPlatformAdapter(
    override val dataFolder: File,
    private val serverSupplier: () -> MinecraftServer?
) : JukeboxPlatform {

    private val logger = LoggerFactory.getLogger("peyajCustomDisc")

    override val haProxySupport: Boolean
        get() = PeyajCustomDiscFabric.config.haProxySupport

    override val adminPassword: String
        get() = PeyajCustomDiscFabric.config.adminPassword

    override val autoReloadGeyser: Boolean
        get() = PeyajCustomDiscFabric.config.autoReloadGeyser

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
        FabricPackUpdater.updateAllPlayers(server)
    }

    override fun dispatchCommand(command: String) {
        val server = serverSupplier() ?: return
        server.commandManager.executeWithPrefix(server.commandSource, command)
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

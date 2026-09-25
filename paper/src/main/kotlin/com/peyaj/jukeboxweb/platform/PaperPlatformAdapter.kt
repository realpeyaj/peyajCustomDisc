package com.peyaj.jukeboxweb.platform

import com.peyaj.jukeboxweb.PeyajCustomDisc
import com.peyaj.jukeboxweb.pack.PackUpdater
import java.io.File

class PaperPlatformAdapter(private val plugin: PeyajCustomDisc) : JukeboxPlatform {

    override val dataFolder: File
        get() = plugin.dataFolder

    override val haProxySupport: Boolean
        get() = plugin.config.getBoolean("haproxy-support", false)

    override val adminPassword: String
        get() = plugin.config.getString("admin-password", "changeme") ?: ""

    override val autoReloadGeyser: Boolean
        get() = plugin.config.getBoolean("auto-reload-geyser", true)

    override fun runSync(task: Runnable) {
        if (plugin.isEnabled) {
            plugin.server.scheduler.runTask(plugin, task)
        } else {
            task.run()
        }
    }

    override fun runAsync(task: Runnable) {
        if (plugin.isEnabled) {
            plugin.server.scheduler.runTaskAsynchronously(plugin, task)
        } else {
            java.util.concurrent.ForkJoinPool.commonPool().execute(task)
        }
    }

    override fun onPackUpdated() {
        PackUpdater.updateAllPlayers(plugin)
    }

    override fun dispatchCommand(command: String) {
        plugin.server.dispatchCommand(plugin.server.consoleSender, command)
    }

    override fun logInfo(message: String) {
        plugin.logger.info(message)
    }

    override fun logWarning(message: String) {
        plugin.logger.warning(message)
    }

    override fun logSevere(message: String) {
        plugin.logger.severe(message)
    }
}

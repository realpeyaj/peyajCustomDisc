package com.peyaj.jukeboxweb.platform

import java.io.File

/**
 * Platform abstraction interface allowing core systems (WebServer, PackGenerator, DiscManager)
 * to run on any Minecraft server platform (Paper, Fabric, NeoForge) without modification.
 */
interface JukeboxPlatform {
    val dataFolder: File
    val haProxySupport: Boolean
    val adminPassword: String
    val autoReloadGeyser: Boolean

    fun runSync(task: Runnable)
    fun runAsync(task: Runnable)
    fun onPackUpdated()
    fun dispatchCommand(command: String)

    fun logInfo(message: String)
    fun logWarning(message: String)
    fun logSevere(message: String)
}

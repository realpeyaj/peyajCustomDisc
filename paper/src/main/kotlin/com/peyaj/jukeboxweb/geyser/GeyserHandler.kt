package com.peyaj.jukeboxweb.geyser

import com.peyaj.jukeboxweb.PeyajCustomDisc
import org.bukkit.Bukkit

object GeyserHandler {

    fun init(plugin: PeyajCustomDisc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") &&
            !Bukkit.getPluginManager().isPluginEnabled("Geyser-Paper") &&
            !Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
            return
        }

        try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val geyserApi = geyserApiClass.getMethod("api").invoke(null)
            val eventBus = geyserApiClass.getMethod("eventBus").invoke(geyserApi)

            val eventClass = Class.forName("org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent")
            val registrarClass = Class.forName("org.geysermc.event.subscribe.EventRegistrar")

            // Create a dynamic proxy for EventRegistrar (marker interface)
            val registrarProxy = java.lang.reflect.Proxy.newProxyInstance(
                registrarClass.classLoader,
                arrayOf(registrarClass)
            ) { _, _, _ -> null }

            // Subscribe to GeyserDefineCustomItemsEvent via reflection
            val subscribeMethod = eventBus.javaClass.methods.find { m ->
                m.name == "subscribe" &&
                m.parameterCount == 3 &&
                m.parameterTypes[1] == Class::class.java
            } ?: throw NoSuchMethodException("subscribe(EventRegistrar, Class, Consumer)")

            val consumer = java.util.function.Consumer<Any> { event ->
                registerCustomItems(plugin, event)
            }

            subscribeMethod.invoke(eventBus, registrarProxy, eventClass, consumer)

            plugin.logger.info("✔ Geyser Bedrock integration initialized.")
        } catch (e: Throwable) {
            plugin.logger.warning("Geyser integration notice: ${e.message}")
        }
    }

    fun isBedrockPlayer(player: org.bukkit.entity.Player): Boolean = isBedrockPlayer(player.uniqueId)

    fun isBedrockPlayer(uuid: java.util.UUID): Boolean {
        // 1. Geyser API Check
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") || 
            Bukkit.getPluginManager().isPluginEnabled("Geyser-Paper") ||
            Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
            try {
                val apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
                val api = apiClass.getMethod("api").invoke(null)
                val isBedrock = apiClass.getMethod("isBedrockPlayer", java.util.UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }

        // 2. Floodgate API Check
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                val api = apiClass.getMethod("getInstance").invoke(null)
                val isBedrock = apiClass.getMethod("isFloodgatePlayer", java.util.UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }

        // 3. Fallback check: Floodgate UUIDs often have 0L mostSignificantBits
        return uuid.mostSignificantBits == 0L
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerCustomItems(plugin: PeyajCustomDisc, event: Any) {
        try {
            val discs = plugin.discManager.getAllDiscs()

            val optionsClass = Class.forName("org.geysermc.geyser.api.item.custom.CustomItemOptions")
            val itemDataClass = Class.forName("org.geysermc.geyser.api.item.custom.CustomItemData")

            for (disc in discs) {
                val discCleanId = disc.cleanId
                val cmd = disc.effectiveCustomModelData

                // Build CustomItemOptions
                val optionsBuilder = optionsClass.getMethod("builder").invoke(null)
                optionsBuilder.javaClass.getMethod("customModelData", Int::class.javaPrimitiveType).invoke(optionsBuilder, cmd)
                val options = optionsBuilder.javaClass.getMethod("build").invoke(optionsBuilder)

                // Build CustomItemData
                val dataBuilder = itemDataClass.getMethod("builder").invoke(null)
                dataBuilder.javaClass.getMethod("name", String::class.java).invoke(dataBuilder, "peyaj_music_disc_$discCleanId")
                dataBuilder.javaClass.getMethod("displayName", String::class.java).invoke(dataBuilder, "Music Disc")
                dataBuilder.javaClass.getMethod("icon", String::class.java).invoke(dataBuilder, "music_disc_$discCleanId")
                dataBuilder.javaClass.getMethod("customItemOptions", optionsClass).invoke(dataBuilder, options)
                val itemData = dataBuilder.javaClass.getMethod("build").invoke(dataBuilder)

                // Register against minecraft:paper since the Java item is Material.PAPER
                val registerMethod = event.javaClass.methods.find { m ->
                    m.name == "register" &&
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == String::class.java
                } ?: throw NoSuchMethodException("register(String, CustomItemData)")

                registerMethod.invoke(event, "minecraft:paper", itemData)

                plugin.logger.info("✔ Geyser: Registered custom item music_disc_$discCleanId (CMD=$cmd) to minecraft:paper")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Geyser custom item registration failed: ${e.message}")
        }
    }
}

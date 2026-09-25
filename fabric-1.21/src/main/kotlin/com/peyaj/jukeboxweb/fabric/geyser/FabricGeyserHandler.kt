package com.peyaj.jukeboxweb.fabric.geyser

import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.loader.api.FabricLoader

object FabricGeyserHandler {

    fun init() {
        if (!FabricLoader.getInstance().isModLoaded("geyser-fabric") &&
            !FabricLoader.getInstance().isModLoaded("geyser")) {
            return
        }

        try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val geyserApi = geyserApiClass.getMethod("api").invoke(null)
            val eventBus = geyserApiClass.getMethod("eventBus").invoke(geyserApi)

            val eventClass = Class.forName("org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent")
            val registrarClass = Class.forName("org.geysermc.event.subscribe.EventRegistrar")

            val registrarProxy = java.lang.reflect.Proxy.newProxyInstance(
                registrarClass.classLoader,
                arrayOf(registrarClass)
            ) { _, _, _ -> null }

            val subscribeMethod = eventBus.javaClass.methods.find { m ->
                m.name == "subscribe" &&
                m.parameterCount == 3 &&
                m.parameterTypes[1] == Class::class.java
            } ?: throw NoSuchMethodException("subscribe(EventRegistrar, Class, Consumer)")

            val consumer = java.util.function.Consumer<Any> { event ->
                registerCustomItems(event)
            }

            subscribeMethod.invoke(eventBus, registrarProxy, eventClass, consumer)
            PeyajCustomDiscFabric.logger.info("✔ Geyser-Fabric Bedrock integration initialized.")
        } catch (e: Throwable) {
            PeyajCustomDiscFabric.logger.warn("Geyser integration notice: ${e.message}")
        }
    }

    private fun registerCustomItems(event: Any) {
        try {
            val discs = PeyajCustomDiscFabric.discManager.getAllDiscs()

            val optionsClass = Class.forName("org.geysermc.geyser.api.item.custom.CustomItemOptions")
            val itemDataClass = Class.forName("org.geysermc.geyser.api.item.custom.CustomItemData")

            for (disc in discs) {
                val discCleanId = disc.id.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                val cmd = if (disc.customModelData != 0) disc.customModelData else (10000 + Math.abs(discCleanId.hashCode()) % 50000)

                val optionsBuilder = optionsClass.getMethod("builder").invoke(null)
                optionsClass.getMethod("customModelData", java.lang.Integer.TYPE).invoke(optionsBuilder, cmd)
                val options = optionsBuilder.javaClass.getMethod("build").invoke(optionsBuilder)

                val dataBuilder = itemDataClass.getMethod("builder").invoke(null)
                itemDataClass.getMethod("name", String::class.java).invoke(dataBuilder, "music_disc_$discCleanId")
                itemDataClass.getMethod("customItemOptions", optionsClass).invoke(dataBuilder, options)
                itemDataClass.getMethod("icon", String::class.java).invoke(dataBuilder, "music_disc_$discCleanId")

                val customItemData = dataBuilder.javaClass.getMethod("build").invoke(dataBuilder)

                val registerMethod = event.javaClass.getMethod("register", String::class.java, itemDataClass)
                registerMethod.invoke(event, "minecraft:paper", customItemData)
            }
            PeyajCustomDiscFabric.logger.info("Registered ${discs.size} custom discs with Geyser Bedrock mapping.")
        } catch (e: Throwable) {
            PeyajCustomDiscFabric.logger.warn("Failed registering Geyser custom items: ${e.message}")
        }
    }
}

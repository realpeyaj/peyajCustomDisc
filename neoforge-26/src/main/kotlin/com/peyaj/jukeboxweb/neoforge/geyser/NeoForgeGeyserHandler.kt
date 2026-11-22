package com.peyaj.jukeboxweb.neoforge.geyser

import com.peyaj.jukeboxweb.neoforge.PeyajCustomDiscNeoForge
import net.neoforged.fml.ModList
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.function.Consumer

object NeoForgeGeyserHandler {

    fun init() {
        if (!ModList.get().isLoaded("geyser_neoforge") &&
            !ModList.get().isLoaded("geyser-neoforge") &&
            !ModList.get().isLoaded("geyser")) {
            return
        }

        try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val geyserApi = geyserApiClass.getMethod("api").invoke(null)
            val eventBus = geyserApiClass.getMethod("eventBus").invoke(geyserApi)

            val eventClass = Class.forName("org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent")
            val registrarClass = Class.forName("org.geysermc.event.subscribe.EventRegistrar")

            val registrarProxy = Proxy.newProxyInstance(
                registrarClass.classLoader,
                arrayOf(registrarClass)
            ) { _, _, _ -> null }

            val subscribeMethod = eventBus.javaClass.methods.find { m ->
                m.name == "subscribe" &&
                    m.parameterCount == 3 &&
                    m.parameterTypes[1] == Class::class.java
            } ?: throw NoSuchMethodException("subscribe(EventRegistrar, Class, Consumer)")

            val consumer = Consumer<Any> { event ->
                registerCustomItems(event)
            }

            subscribeMethod.invoke(eventBus, registrarProxy, eventClass, consumer)
            PeyajCustomDiscNeoForge.logger.info("Geyser-NeoForge Bedrock integration initialized.")
        } catch (e: Throwable) {
            PeyajCustomDiscNeoForge.logger.warn("Geyser integration notice: ${e.message}")
        }
    }

    private fun registerCustomItems(event: Any) {
        try {
            val discs = PeyajCustomDiscNeoForge.discManager.getAllDiscs()

            val optionsClass = Class.forName("org.geysermc.geyser.api.item.custom.CustomItemOptions")
            val itemDataClass = Class.forName("org.geysermc.geyser.api.item.custom.CustomItemData")

            for (disc in discs) {
                val discCleanId = disc.cleanId
                val cmd = disc.effectiveCustomModelData

                val optionsBuilder = optionsClass.getMethod("builder").invoke(null)
                optionsClass.getMethod("customModelData", Integer.TYPE).invoke(optionsBuilder, cmd)
                val options = optionsBuilder.javaClass.getMethod("build").invoke(optionsBuilder)

                val dataBuilder = itemDataClass.getMethod("builder").invoke(null)
                itemDataClass.getMethod("name", String::class.java).invoke(dataBuilder, "music_disc_$discCleanId")
                itemDataClass.getMethod("customItemOptions", optionsClass).invoke(dataBuilder, options)
                itemDataClass.getMethod("icon", String::class.java).invoke(dataBuilder, "music_disc_$discCleanId")

                val customItemData = dataBuilder.javaClass.getMethod("build").invoke(dataBuilder)

                val registerMethod = event.javaClass.getMethod("register", String::class.java, itemDataClass)
                registerMethod.invoke(event, "minecraft:paper", customItemData)
            }
            PeyajCustomDiscNeoForge.logger.info("Registered ${discs.size} custom discs with Geyser Bedrock mapping.")
        } catch (e: Throwable) {
            PeyajCustomDiscNeoForge.logger.warn("Failed registering Geyser custom items: ${e.message}")
        }
    }

    fun isBedrockPlayer(uuid: UUID): Boolean {
        try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val api = geyserApiClass.getMethod("api").invoke(null)
            val isBedrock = geyserApiClass.getMethod("isBedrockPlayer", UUID::class.java).invoke(api, uuid) as Boolean
            if (isBedrock) return true
        } catch (_: Exception) {
        }
        return uuid.mostSignificantBits == 0L
    }
}

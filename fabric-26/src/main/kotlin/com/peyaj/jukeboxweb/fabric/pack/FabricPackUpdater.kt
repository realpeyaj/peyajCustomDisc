package com.peyaj.jukeboxweb.fabric.pack

import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.util.Optional
import java.util.UUID

object FabricPackUpdater {

    private val JAVA_PACK_UUID: UUID = UUID.nameUUIDFromBytes("peyajcustomdisc:java-pack".toByteArray())

    fun register() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            val config = PeyajCustomDiscFabric.config
            if (!config.sendOnJoin) return@register
            if (isBedrockPlayer(player)) return@register

            val urlBase = config.publicUrl.trimEnd('/')
            val zipUrl = "$urlBase/download/pack"

            server.execute {
                var hash = PeyajCustomDiscFabric.packGenerator.getPackHash()
                if (hash.isEmpty()) {
                    try {
                        PeyajCustomDiscFabric.packGenerator.buildResourcePack(PeyajCustomDiscFabric.discManager.getAllDiscs())
                        hash = PeyajCustomDiscFabric.packGenerator.getPackHash()
                    } catch (e: Exception) {
                        PeyajCustomDiscFabric.logger.warn("Failed to generate pack hash for join delivery: ${e.message}")
                    }
                }

                if (hash.isNotEmpty()) {
                    try {
                        val promptText = Component.literal(config.prompt)
                        val packet = ClientboundResourcePackPushPacket(
                            JAVA_PACK_UUID,
                            zipUrl,
                            hash,
                            config.required,
                            Optional.of(promptText)
                        )
                        player.connection.send(packet)
                    } catch (e: Exception) {
                        PeyajCustomDiscFabric.logger.warn("Failed to send resource pack to ${player.name.string}: ${e.message}")
                    }
                }
            }
        }
    }

    fun updateAllPlayers(server: MinecraftServer) {
        updateGeyserPack(forceReload = true)

        val config = PeyajCustomDiscFabric.config
        if (!config.autoUpdatePack) return

        val urlBase = config.publicUrl.trimEnd('/')
        val zipUrl = "$urlBase/download/pack"
        val hash = PeyajCustomDiscFabric.packGenerator.getPackHash()

        if (hash.isNotEmpty()) {
            val promptText = Component.literal(config.prompt)
            val packet = ClientboundResourcePackPushPacket(
                JAVA_PACK_UUID,
                zipUrl,
                hash,
                config.required,
                Optional.of(promptText)
            )
            for (player in server.playerList.players) {
                if (!isBedrockPlayer(player)) {
                    try {
                        player.connection.send(packet)
                    } catch (e: Exception) {
                        PeyajCustomDiscFabric.logger.warn("Failed to send resource pack to ${player.name.string}: ${e.message}")
                    }
                }
            }
        }
    }

    fun updateGeyserPack(forceReload: Boolean = false) {
        val bedrockPackFile = PeyajCustomDiscFabric.packGenerator.getBedrockPackFile()
        if (!bedrockPackFile.exists()) return

        val configDir = FabricLoader.getInstance().configDir.toFile()
        val geyserDirs = listOf(
            File(configDir, "Geyser-Fabric"),
            File(configDir, "Geyser")
        )

        geyserDirs.forEach { geyserDir ->
            if (geyserDir.exists()) {
                try {
                    val packsDir = File(geyserDir, "packs")
                    packsDir.mkdirs()
                    bedrockPackFile.copyTo(File(packsDir, "peyajCD-Bedrock.mcpack"), overwrite = true)
                    if (forceReload && PeyajCustomDiscFabric.config.autoReloadGeyser) {
                        val server = PeyajCustomDiscFabric.server
                        server?.commands?.performPrefixedCommand(server.createCommandSourceStack(), "geyser reload")
                    }
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun isBedrockPlayer(player: ServerPlayer): Boolean {
        try {
            val geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val api = geyserApiClass.getMethod("api").invoke(null)
            val isBedrock = geyserApiClass.getMethod("isBedrockPlayer", UUID::class.java).invoke(api, player.uuid) as Boolean
            if (isBedrock) return true
        } catch (ignored: Exception) {}
        return player.uuid.mostSignificantBits == 0L
    }
}

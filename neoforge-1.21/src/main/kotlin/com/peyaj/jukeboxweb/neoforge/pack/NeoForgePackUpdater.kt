package com.peyaj.jukeboxweb.neoforge.pack

import com.peyaj.jukeboxweb.neoforge.PeyajCustomDiscNeoForge
import com.peyaj.jukeboxweb.neoforge.geyser.NeoForgeGeyserHandler
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import java.io.File
import java.util.Optional
import java.util.UUID

object NeoForgePackUpdater {

    private val JAVA_PACK_UUID: UUID = UUID.nameUUIDFromBytes("peyajcustomdisc:java-pack".toByteArray())

    fun register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent::class.java) { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            val server = PeyajCustomDiscNeoForge.server ?: return@addListener
            val config = PeyajCustomDiscNeoForge.config
            if (!config.sendOnJoin) return@addListener
            if (isBedrockPlayer(player)) return@addListener

            val urlBase = config.publicUrl.trimEnd('/')
            val zipUrl = "$urlBase/download/pack"

            server.execute {
                var hash = PeyajCustomDiscNeoForge.packGenerator.getPackHash()
                if (hash.isEmpty()) {
                    try {
                        PeyajCustomDiscNeoForge.packGenerator.buildResourcePack(PeyajCustomDiscNeoForge.discManager.getAllDiscs())
                        hash = PeyajCustomDiscNeoForge.packGenerator.getPackHash()
                    } catch (e: Exception) {
                        PeyajCustomDiscNeoForge.logger.warn("Failed to generate pack hash for join delivery: ${e.message}")
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
                        PeyajCustomDiscNeoForge.logger.warn("Failed to send resource pack to ${player.name.string}: ${e.message}")
                    }
                }
            }
        }
    }

    fun updateAllPlayers(server: MinecraftServer) {
        updateGeyserPack(forceReload = true)

        val config = PeyajCustomDiscNeoForge.config
        if (!config.autoUpdatePack) return

        val urlBase = config.publicUrl.trimEnd('/')
        val zipUrl = "$urlBase/download/pack"
        val hash = PeyajCustomDiscNeoForge.packGenerator.getPackHash()

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
                        PeyajCustomDiscNeoForge.logger.warn("Failed to send resource pack to ${player.name.string}: ${e.message}")
                    }
                }
            }
        }
    }

    fun updateGeyserPack(forceReload: Boolean = false) {
        val bedrockPackFile = PeyajCustomDiscNeoForge.packGenerator.getBedrockPackFile()
        if (!bedrockPackFile.exists()) return

        val configDir = FMLPaths.CONFIGDIR.get().toFile()
        val geyserDirs = listOf(
            File(configDir, "Geyser-NeoForge"),
            File(configDir, "Geyser")
        )

        geyserDirs.forEach { geyserDir ->
            if (geyserDir.exists()) {
                try {
                    val packsDir = File(geyserDir, "packs")
                    packsDir.mkdirs()
                    bedrockPackFile.copyTo(File(packsDir, "peyajCD-Bedrock.mcpack"), overwrite = true)
                    if (forceReload && PeyajCustomDiscNeoForge.config.autoReloadGeyser) {
                        val server = PeyajCustomDiscNeoForge.server
                        server?.commands?.performPrefixedCommand(server.createCommandSourceStack(), "geyser reload")
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isBedrockPlayer(player: ServerPlayer): Boolean {
        return NeoForgeGeyserHandler.isBedrockPlayer(player.uuid)
    }
}

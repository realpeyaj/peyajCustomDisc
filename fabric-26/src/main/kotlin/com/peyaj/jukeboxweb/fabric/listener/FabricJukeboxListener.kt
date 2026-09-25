package com.peyaj.jukeboxweb.fabric.listener

import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import java.util.concurrent.ConcurrentHashMap

object FabricJukeboxListener {

    private data class PlayingSession(
        val discId: String,
        val startTime: Long,
        val durationSeconds: Int,
        var ticksRemaining: Int
    )

    private val activeJukeboxes = ConcurrentHashMap<BlockPos, PlayingSession>()

    fun register() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            val pos = hitResult.blockPos
            val state = world.getBlockState(pos)
            if (!state.`is`(Blocks.JUKEBOX)) return@register InteractionResult.PASS

            val discMgr = PeyajCustomDiscFabric.discManager

            // 1. If jukebox is already playing a custom disc -> Eject & Stop
            val existing = activeJukeboxes[pos]
            if (existing != null) {
                stopPlaying(world as ServerLevel, pos, existing.discId)
                
                // Drop disc item
                val discItem = discMgr.createDiscItem(existing.discId)
                if (discItem != null) {
                    val drop = ItemEntity(world, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, discItem)
                    world.addFreshEntity(drop)
                }
                return@register InteractionResult.SUCCESS
            }

            // 2. If player is holding a custom disc -> Play
            val heldItem = player.getItemInHand(hand)
            if (discMgr.isCustomDisc(heldItem)) {
                val discId = discMgr.getDiscIdFromItem(heldItem) ?: return@register InteractionResult.PASS
                val disc = discMgr.getDisc(discId) ?: return@register InteractionResult.PASS

                if (!player.abilities.instabuild) {
                    heldItem.shrink(1)
                }

                val duration = if (disc.durationSeconds > 0) disc.durationSeconds else 180
                activeJukeboxes[pos] = PlayingSession(
                    discId = disc.id,
                    startTime = System.currentTimeMillis(),
                    durationSeconds = duration,
                    ticksRemaining = duration * 20
                )

                val soundId = Identifier.fromNamespaceAndPath("peyajcustomdisc", "disc.${disc.id}")
                val soundEvent = SoundEvent.createVariableRangeEvent(soundId)
                world.playSound(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, soundEvent, SoundSource.RECORDS, 1.0f, 1.0f)

                val msg = Component.literal("♫ Now Playing: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(disc.name).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" by ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(disc.author).withStyle(ChatFormatting.YELLOW))

                if (player is ServerPlayer) {
                    player.sendSystemMessage(msg, true)
                }

                if (world is ServerLevel) {
                    world.sendParticles(ParticleTypes.NOTE, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 3, 0.2, 0.2, 0.2, 0.0)
                }

                return@register InteractionResult.SUCCESS
            }

            InteractionResult.PASS
        }

        // On block break, clean up active session and drop disc
        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, _ ->
            if (world.isClientSide) return@register
            val session = activeJukeboxes.remove(pos) ?: return@register
            stopPlaying(world as ServerLevel, pos, session.discId)
            val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
            if (discItem != null) {
                val drop = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, discItem)
                world.addFreshEntity(drop)
            }
        }

        // Periodic tick update: particle emissions and session expiration
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val toRemove = mutableListOf<BlockPos>()
            activeJukeboxes.forEach { (pos, session) ->
                session.ticksRemaining--
                if (session.ticksRemaining <= 0) {
                    toRemove.add(pos)
                } else if (session.ticksRemaining % 20 == 0) {
                    server.allLevels.forEach { world ->
                        if (world.getBlockState(pos).`is`(Blocks.JUKEBOX)) {
                            world.sendParticles(ParticleTypes.NOTE, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 1, 0.1, 0.1, 0.1, 0.0)
                        }
                    }
                }
            }
            toRemove.forEach { pos ->
                val session = activeJukeboxes.remove(pos) ?: return@forEach
                server.allLevels.forEach { world ->
                    if (world.getBlockState(pos).`is`(Blocks.JUKEBOX)) {
                        stopPlaying(world, pos, session.discId)
                        val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
                        if (discItem != null) {
                            val drop = ItemEntity(world, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, discItem)
                            world.addFreshEntity(drop)
                        }
                    }
                }
            }
        }
    }

    private fun stopPlaying(world: ServerLevel, pos: BlockPos, discId: String) {
        val soundId = Identifier.fromNamespaceAndPath("peyajcustomdisc", "disc.$discId")
        val packet = ClientboundStopSoundPacket(soundId, SoundSource.RECORDS)
        world.server.playerList.players.forEach { p ->
            p.connection.send(packet)
        }
    }
}

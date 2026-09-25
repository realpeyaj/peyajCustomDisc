package com.peyaj.jukeboxweb.fabric.listener

import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
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
            if (world.isClient || hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            val pos = hitResult.blockPos
            val state = world.getBlockState(pos)
            if (!state.isOf(Blocks.JUKEBOX)) return@register ActionResult.PASS

            val discMgr = PeyajCustomDiscFabric.discManager

            // 1. If jukebox is already playing a custom disc -> Eject & Stop
            val existing = activeJukeboxes[pos]
            if (existing != null) {
                stopPlaying(world as ServerWorld, pos, existing.discId)
                
                // Drop disc item
                val discItem = discMgr.createDiscItem(existing.discId)
                if (discItem != null) {
                    val drop = ItemEntity(world, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, discItem)
                    world.spawnEntity(drop)
                }
                return@register ActionResult.SUCCESS
            }

            // 2. If player is holding a custom disc -> Play
            val heldItem = player.getStackInHand(hand)
            if (discMgr.isCustomDisc(heldItem)) {
                val discId = discMgr.getDiscIdFromItem(heldItem) ?: return@register ActionResult.PASS
                val disc = discMgr.getDisc(discId) ?: return@register ActionResult.PASS

                if (!player.isCreative) {
                    heldItem.decrement(1)
                }

                val duration = if (disc.durationSeconds > 0) disc.durationSeconds else 180
                activeJukeboxes[pos] = PlayingSession(
                    discId = disc.id,
                    startTime = System.currentTimeMillis(),
                    durationSeconds = duration,
                    ticksRemaining = duration * 20
                )

                val soundId = Identifier.of("peyajcustomdisc", "disc.${disc.id}")
                val soundEvent = SoundEvent.of(soundId)
                world.playSound(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, soundEvent, SoundCategory.RECORDS, 1.0f, 1.0f)

                val msg = Text.literal("♫ Now Playing: ").formatted(Formatting.GOLD)
                    .append(Text.literal(disc.name).formatted(Formatting.AQUA))
                    .append(Text.literal(" by ").formatted(Formatting.GRAY))
                    .append(Text.literal(disc.author).formatted(Formatting.YELLOW))

                if (player is ServerPlayerEntity) {
                    player.sendMessage(msg, true)
                }

                if (world is ServerWorld) {
                    world.spawnParticles(ParticleTypes.NOTE, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 3, 0.2, 0.2, 0.2, 0.0)
                }

                return@register ActionResult.SUCCESS
            }

            ActionResult.PASS
        }

        // On block break, clean up active session and drop disc
        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, _ ->
            if (world.isClient) return@register
            val session = activeJukeboxes.remove(pos) ?: return@register
            stopPlaying(world as ServerWorld, pos, session.discId)
            val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
            if (discItem != null) {
                val drop = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, discItem)
                world.spawnEntity(drop)
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
                    server.worlds.forEach { world ->
                        if (world.getBlockState(pos).isOf(Blocks.JUKEBOX)) {
                            world.spawnParticles(ParticleTypes.NOTE, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 1, 0.1, 0.1, 0.1, 0.0)
                        }
                    }
                }
            }
            toRemove.forEach { pos ->
                val session = activeJukeboxes.remove(pos) ?: return@forEach
                server.worlds.forEach { world ->
                    if (world.getBlockState(pos).isOf(Blocks.JUKEBOX)) {
                        stopPlaying(world, pos, session.discId)
                        val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
                        if (discItem != null) {
                            val drop = ItemEntity(world, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, discItem)
                            world.spawnEntity(drop)
                        }
                    }
                }
            }
        }
    }

    private fun stopPlaying(world: ServerWorld, pos: BlockPos, discId: String) {
        val soundId = Identifier.of("peyajcustomdisc", "disc.$discId")
        val packet = StopSoundS2CPacket(soundId, SoundCategory.RECORDS)
        world.server.playerManager.playerList.forEach { p ->
            p.networkHandler.sendPacket(packet)
        }
    }
}

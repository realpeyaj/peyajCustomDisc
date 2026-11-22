package com.peyaj.jukeboxweb.fabric.listener

import com.peyaj.jukeboxweb.disc.CustomDisc
import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.passive.AllayEntity
import net.minecraft.entity.passive.ParrotEntity
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
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
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.event.GameEvent
import java.util.concurrent.ConcurrentHashMap

object FabricJukeboxListener {

    private const val DANCE_EVENT_INTERVAL_TICKS = 60

    private data class JukeboxKey(
        val worldKey: RegistryKey<World>,
        val pos: BlockPos
    )

    private data class PlayingSession(
        val discId: String,
        val discName: String,
        val discAuthor: String,
        val durationSeconds: Int,
        var ticksRemaining: Int,
        var danceTickCounter: Int = 0
    )

    private val activeJukeboxes = ConcurrentHashMap<JukeboxKey, PlayingSession>()

    fun register() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClient || hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            val pos = hitResult.blockPos
            val state = world.getBlockState(pos)
            if (!state.isOf(Blocks.JUKEBOX)) return@register ActionResult.PASS

            val serverWorld = world as? ServerWorld ?: return@register ActionResult.PASS
            val key = JukeboxKey(serverWorld.registryKey, pos.toImmutable())
            val discMgr = PeyajCustomDiscFabric.discManager

            // 1. If jukebox is already playing a custom disc -> atomically remove session, stop & eject 1 disc
            val existing = activeJukeboxes.remove(key)
            if (existing != null) {
                stopPlaying(serverWorld, pos, existing.discId)
                stopNearbyDancing(serverWorld, pos)

                val discItem = discMgr.createDiscItem(existing.discId)
                if (discItem != null) {
                    val drop = ItemEntity(serverWorld, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, discItem)
                    serverWorld.spawnEntity(drop)
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
                activeJukeboxes[key] = PlayingSession(
                    discId = disc.id,
                    discName = disc.name,
                    discAuthor = disc.author,
                    durationSeconds = duration,
                    ticksRemaining = duration * 20
                )

                val msg = buildNowPlayingText(disc.name, disc.author)

                // Notify nearby Parrots & Allays before/around custom audio so custom audio + actionbar override vanilla stub
                startNearbyDancing(serverWorld, pos, disc.name, disc.author)

                val soundId = Identifier.of("peyajcustomdisc", "disc.${disc.cleanId}")
                val soundEvent = SoundEvent.of(soundId)
                serverWorld.playSound(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, soundEvent, SoundCategory.RECORDS, 1.0f, 1.0f)

                if (player is ServerPlayerEntity) {
                    player.sendMessage(msg, true)
                }

                serverWorld.spawnParticles(ParticleTypes.NOTE, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 3, 0.2, 0.2, 0.2, 0.0)

                return@register ActionResult.SUCCESS
            }

            ActionResult.PASS
        }

        // On block break, atomically remove active session and drop 1 disc
        PlayerBlockBreakEvents.AFTER.register { world, _, pos, _, _ ->
            if (world.isClient) return@register
            val serverWorld = world as? ServerWorld ?: return@register
            val key = JukeboxKey(serverWorld.registryKey, pos.toImmutable())
            val session = activeJukeboxes.remove(key) ?: return@register
            stopPlaying(serverWorld, pos, session.discId)
            stopNearbyDancing(serverWorld, pos)
            val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
            if (discItem != null) {
                val drop = ItemEntity(serverWorld, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, discItem)
                serverWorld.spawnEntity(drop)
            }
        }

        // Periodic tick update: particle emissions, mob dancing pulse, and session expiration
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val expiredOrBroken = mutableListOf<JukeboxKey>()
            activeJukeboxes.forEach { (key, session) ->
                val world = server.getWorld(key.worldKey)
                if (world == null) {
                    expiredOrBroken.add(key)
                    return@forEach
                }

                session.ticksRemaining--
                session.danceTickCounter++

                val isLoaded = world.isChunkLoaded(key.pos.x shr 4, key.pos.z shr 4)
                if (isLoaded && !world.getBlockState(key.pos).isOf(Blocks.JUKEBOX)) {
                    expiredOrBroken.add(key)
                    return@forEach
                }

                if (session.ticksRemaining <= 0) {
                    expiredOrBroken.add(key)
                } else if (isLoaded) {
                    if (session.ticksRemaining % 20 == 0) {
                        world.spawnParticles(
                            ParticleTypes.NOTE,
                            key.pos.x + 0.5,
                            key.pos.y + 1.2,
                            key.pos.z + 0.5,
                            1,
                            0.1,
                            0.1,
                            0.1,
                            0.0
                        )
                    }
                    if (session.danceTickCounter >= DANCE_EVENT_INTERVAL_TICKS) {
                        session.danceTickCounter = 0
                        pulseNearbyDancing(world, key.pos, session.discName, session.discAuthor)
                    }
                }
            }

            expiredOrBroken.forEach { key ->
                val session = activeJukeboxes.remove(key) ?: return@forEach
                val world = server.getWorld(key.worldKey) ?: return@forEach
                stopPlaying(world, key.pos, session.discId)
                if (world.isChunkLoaded(key.pos.x shr 4, key.pos.z shr 4)) {
                    stopNearbyDancing(world, key.pos)
                    if (world.getBlockState(key.pos).isOf(Blocks.JUKEBOX)) {
                        val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
                        if (discItem != null) {
                            val drop = ItemEntity(world, key.pos.x + 0.5, key.pos.y + 1.0, key.pos.z + 0.5, discItem)
                            world.spawnEntity(drop)
                        }
                    }
                }
            }
        }
    }

    private fun buildNowPlayingText(name: String, author: String): Text {
        return Text.literal("♫ Now Playing: ").formatted(Formatting.GOLD)
            .append(Text.literal(name).formatted(Formatting.AQUA))
            .append(Text.literal(" by ").formatted(Formatting.GRAY))
            .append(Text.literal(author).formatted(Formatting.YELLOW))
    }

    private fun startNearbyDancing(world: ServerWorld, pos: BlockPos, discName: String, discAuthor: String) {
        val state = world.getBlockState(pos)
        world.emitGameEvent(GameEvent.JUKEBOX_PLAY, Vec3d.ofCenter(pos), GameEvent.Emitter.of(state))

        val allayBox = Box(pos).expand(10.0)
        world.getEntitiesByClass(AllayEntity::class.java, allayBox) { true }.forEach { allay ->
            allay.updateJukeboxPos(pos, true)
        }

        notifyNewNearbyParrots(world, pos, discName, discAuthor)
    }

    private fun pulseNearbyDancing(world: ServerWorld, pos: BlockPos, discName: String, discAuthor: String) {
        val state = world.getBlockState(pos)
        world.emitGameEvent(GameEvent.JUKEBOX_PLAY, Vec3d.ofCenter(pos), GameEvent.Emitter.of(state))

        val allayBox = Box(pos).expand(10.0)
        world.getEntitiesByClass(AllayEntity::class.java, allayBox) { !it.isDancing }.forEach { allay ->
            allay.updateJukeboxPos(pos, true)
        }

        notifyNewNearbyParrots(world, pos, discName, discAuthor)
    }

    /**
     * Triggers client-side parrot dance via world event 1010 only when a non-dancing parrot is in range,
     * then immediately cancels the dummy vanilla song sound and restores the custom Now Playing title.
     */
    private fun notifyNewNearbyParrots(world: ServerWorld, pos: BlockPos, discName: String, discAuthor: String) {
        val parrotBox = Box(pos).expand(3.5)
        val parrotsToStart = world.getEntitiesByClass(ParrotEntity::class.java, parrotBox) { !it.isSongPlaying }
        if (parrotsToStart.isEmpty()) return

        parrotsToStart.forEach { it.setNearbySongPlaying(pos, true) }

        val songRegistry = world.registryManager.get(RegistryKeys.JUKEBOX_SONG)
        val firstSong = songRegistry.getEntry(0)
        if (firstSong.isPresent) {
            val vanillaSoundId = firstSong.get().value().soundEvent().value().id
            world.syncWorldEvent(null, 1010, pos, 0)

            val stopPacket = StopSoundS2CPacket(vanillaSoundId, SoundCategory.RECORDS)
            val nowPlayingMsg = buildNowPlayingText(discName, discAuthor)
            val center = Vec3d.ofCenter(pos)
            world.players.forEach { p ->
                if (p.squaredDistanceTo(center) <= 4096.0) {
                    p.networkHandler.sendPacket(stopPacket)
                    p.sendMessage(nowPlayingMsg, true)
                }
            }
        }
    }

    private fun stopNearbyDancing(world: ServerWorld, pos: BlockPos) {
        val state = world.getBlockState(pos)
        world.emitGameEvent(GameEvent.JUKEBOX_STOP_PLAY, Vec3d.ofCenter(pos), GameEvent.Emitter.of(state))

        val allayBox = Box(pos).expand(12.0)
        world.getEntitiesByClass(AllayEntity::class.java, allayBox) { it.isDancing }.forEach { allay ->
            allay.updateJukeboxPos(pos, false)
        }

        val parrotBox = Box(pos).expand(4.5)
        val nearbyParrots = world.getEntitiesByClass(ParrotEntity::class.java, parrotBox) { true }
        if (nearbyParrots.isNotEmpty()) {
            nearbyParrots.forEach { it.setNearbySongPlaying(pos, false) }
            world.syncWorldEvent(null, 1011, pos, 0)
        }
    }

    private fun stopPlaying(world: ServerWorld, pos: BlockPos, discId: String) {
        val soundId = Identifier.of("peyajcustomdisc", "disc.${CustomDisc.cleanId(discId)}")
        val packet = StopSoundS2CPacket(soundId, SoundCategory.RECORDS)
        world.server.playerManager.playerList.forEach { p ->
            p.networkHandler.sendPacket(packet)
        }
    }
}

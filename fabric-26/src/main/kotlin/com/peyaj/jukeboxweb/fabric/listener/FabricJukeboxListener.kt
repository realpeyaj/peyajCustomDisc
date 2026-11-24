package com.peyaj.jukeboxweb.fabric.listener

import com.peyaj.jukeboxweb.disc.CustomDisc
import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object FabricJukeboxListener {

    private const val DANCE_EVENT_INTERVAL_TICKS = 60
    private val SILENT_SONG_ID = Identifier.fromNamespaceAndPath("peyajcustomdisc", "silent")
    private val STOP_RETRY_TICKS = intArrayOf(1, 2, 4, 8, 15)

    private data class JukeboxKey(
        val worldKey: ResourceKey<Level>,
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

    private data class PendingVanillaStop(
        val worldKey: ResourceKey<Level>,
        val center: Vec3,
        val soundId: Identifier,
        val nowPlayingMsg: Component,
        var ticksElapsed: Int = 0
    )

    private val activeJukeboxes = ConcurrentHashMap<JukeboxKey, PlayingSession>()
    private val pendingVanillaStops = CopyOnWriteArrayList<PendingVanillaStop>()

    fun register() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            val pos = hitResult.blockPos
            val state = world.getBlockState(pos)
            if (!state.`is`(Blocks.JUKEBOX)) return@register InteractionResult.PASS

            val serverWorld = world as? ServerLevel ?: return@register InteractionResult.PASS
            val key = JukeboxKey(serverWorld.dimension(), pos.immutable())
            val discMgr = PeyajCustomDiscFabric.discManager

            // 1. If jukebox is already playing a custom disc -> atomically remove session, stop & eject 1 disc
            val existing = activeJukeboxes.remove(key)
            if (existing != null) {
                stopPlaying(serverWorld, pos, existing.discId)
                stopNearbyDancing(serverWorld, pos)

                val discItem = discMgr.createDiscItem(existing.discId)
                if (discItem != null) {
                    val drop = ItemEntity(serverWorld, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, discItem)
                    serverWorld.addFreshEntity(drop)
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
                activeJukeboxes[key] = PlayingSession(
                    discId = disc.id,
                    discName = disc.name,
                    discAuthor = disc.author,
                    durationSeconds = duration,
                    ticksRemaining = duration * 20
                )

                val msg = buildNowPlayingText(disc.name, disc.author)

                // Notify nearby Parrots & Allays before custom audio so custom audio & actionbar override dummy song
                startNearbyDancing(serverWorld, pos, disc.name, disc.author)

                val soundId = Identifier.fromNamespaceAndPath("peyajcustomdisc", "disc.${disc.cleanId}")
                val soundEvent = SoundEvent.createVariableRangeEvent(soundId)
                serverWorld.playSound(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, soundEvent, SoundSource.RECORDS, 1.0f, 1.0f)

                if (player is ServerPlayer) {
                    player.sendSystemMessage(msg, true)
                }

                serverWorld.sendParticles(ParticleTypes.NOTE, pos.x + 0.5, pos.y + 1.2, pos.z + 0.5, 3, 0.2, 0.2, 0.2, 0.0)

                return@register InteractionResult.SUCCESS
            }

            InteractionResult.PASS
        }

        // On block break, atomically remove active session and drop 1 disc
        PlayerBlockBreakEvents.AFTER.register { world, _, pos, _, _ ->
            if (world.isClientSide) return@register
            val serverWorld = world as? ServerLevel ?: return@register
            val key = JukeboxKey(serverWorld.dimension(), pos.immutable())
            val session = activeJukeboxes.remove(key) ?: return@register
            stopPlaying(serverWorld, pos, session.discId)
            stopNearbyDancing(serverWorld, pos)
            val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
            if (discItem != null) {
                val drop = ItemEntity(serverWorld, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, discItem)
                serverWorld.addFreshEntity(drop)
            }
        }

        // Periodic tick update: particle emissions, mob dancing pulse, and session expiration
        ServerTickEvents.END_SERVER_TICK.register { server ->
            processPendingVanillaStops(server)
            val expiredOrBroken = mutableListOf<JukeboxKey>()
            activeJukeboxes.forEach { (key, session) ->
                val world = server.getLevel(key.worldKey)
                if (world == null) {
                    expiredOrBroken.add(key)
                    return@forEach
                }

                session.ticksRemaining--
                session.danceTickCounter++

                val isLoaded = world.isLoaded(key.pos)
                if (isLoaded && !world.getBlockState(key.pos).`is`(Blocks.JUKEBOX)) {
                    expiredOrBroken.add(key)
                    return@forEach
                }

                if (session.ticksRemaining <= 0) {
                    expiredOrBroken.add(key)
                } else if (isLoaded) {
                    if (session.ticksRemaining % 20 == 0) {
                        world.sendParticles(
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
                val world = server.getLevel(key.worldKey) ?: return@forEach
                stopPlaying(world, key.pos, session.discId)
                if (world.isLoaded(key.pos)) {
                    stopNearbyDancing(world, key.pos)
                    if (world.getBlockState(key.pos).`is`(Blocks.JUKEBOX)) {
                        val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(session.discId)
                        if (discItem != null) {
                            val drop = ItemEntity(world, key.pos.x + 0.5, key.pos.y + 1.0, key.pos.z + 0.5, discItem)
                            world.addFreshEntity(drop)
                        }
                    }
                }
            }
        }
    }

    private fun buildNowPlayingText(name: String, author: String): Component {
        return Component.literal("♫ Now Playing: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(name).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" by ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(author).withStyle(ChatFormatting.YELLOW))
    }

    private fun startNearbyDancing(world: ServerLevel, pos: BlockPos, discName: String, discAuthor: String) {
        val state = world.getBlockState(pos)
        world.gameEvent(GameEvent.JUKEBOX_PLAY, Vec3.atCenterOf(pos), GameEvent.Context.of(state))

        val allayBox = AABB(pos).inflate(10.0)
        world.getEntitiesOfClass(Allay::class.java, allayBox).forEach { allay ->
            allay.setJukeboxPlaying(pos, true)
        }

        notifyNewNearbyParrots(world, pos, discName, discAuthor)
    }

    private fun pulseNearbyDancing(world: ServerLevel, pos: BlockPos, discName: String, discAuthor: String) {
        val state = world.getBlockState(pos)
        world.gameEvent(GameEvent.JUKEBOX_PLAY, Vec3.atCenterOf(pos), GameEvent.Context.of(state))

        val allayBox = AABB(pos).inflate(10.0)
        world.getEntitiesOfClass(Allay::class.java, allayBox).forEach { allay ->
            if (!allay.isDancing) {
                allay.setJukeboxPlaying(pos, true)
            }
        }

        notifyNewNearbyParrots(world, pos, discName, discAuthor)
    }

    /**
     * Triggers client-side parrot dance via level event 1010 using the silent datapack JukeboxSong
     * so zero vanilla disc audio starts. Falls back to multi-tick StopSound retries only if absent.
     */
    private fun notifyNewNearbyParrots(world: ServerLevel, pos: BlockPos, discName: String, discAuthor: String) {
        val parrotBox = AABB(pos).inflate(3.5)
        val parrotsToStart = world.getEntitiesOfClass(Parrot::class.java, parrotBox).filter { !it.isPartyParrot }
        if (parrotsToStart.isEmpty()) return

        parrotsToStart.forEach { it.setRecordPlayingNearby(pos, true) }

        val songRegistry = world.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG)
        val nowPlayingMsg = buildNowPlayingText(discName, discAuthor)
        val center = Vec3.atCenterOf(pos)

        val silentSong = songRegistry.getValue(SILENT_SONG_ID)
        val silentRawId = if (silentSong != null) songRegistry.getId(silentSong) else -1
        if (silentRawId >= 0) {
            world.levelEvent(null, 1010, pos, silentRawId)
            world.players().forEach { p ->
                if (p.distanceToSqr(center) <= 4096.0) {
                    p.sendSystemMessage(nowPlayingMsg, true)
                }
            }
            return
        }

        val firstSong = songRegistry.get(0)
        if (firstSong.isPresent) {
            val vanillaSoundId = firstSong.get().value().soundEvent().value().location()
            world.levelEvent(null, 1010, pos, 0)

            val stopPacket = ClientboundStopSoundPacket(vanillaSoundId, SoundSource.RECORDS)
            world.players().forEach { p ->
                if (p.distanceToSqr(center) <= 4096.0) {
                    p.connection.send(stopPacket)
                    p.sendSystemMessage(nowPlayingMsg, true)
                }
            }
            pendingVanillaStops.add(PendingVanillaStop(world.dimension(), center, vanillaSoundId, nowPlayingMsg))
        }
    }

    private fun processPendingVanillaStops(server: net.minecraft.server.MinecraftServer) {
        if (pendingVanillaStops.isEmpty()) return
        val completed = mutableListOf<PendingVanillaStop>()
        for (entry in pendingVanillaStops) {
            entry.ticksElapsed++
            if (entry.ticksElapsed in STOP_RETRY_TICKS) {
                val world = server.getLevel(entry.worldKey)
                if (world != null) {
                    val stopPacket = ClientboundStopSoundPacket(entry.soundId, SoundSource.RECORDS)
                    world.players().forEach { p ->
                        if (p.distanceToSqr(entry.center) <= 4096.0) {
                            p.connection.send(stopPacket)
                            if (entry.ticksElapsed == 1) {
                                p.sendSystemMessage(entry.nowPlayingMsg, true)
                            }
                        }
                    }
                }
            }
            if (entry.ticksElapsed >= 15) {
                completed.add(entry)
            }
        }
        if (completed.isNotEmpty()) {
            pendingVanillaStops.removeAll(completed.toSet())
        }
    }

    private fun stopNearbyDancing(world: ServerLevel, pos: BlockPos) {
        val state = world.getBlockState(pos)
        world.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, Vec3.atCenterOf(pos), GameEvent.Context.of(state))

        val allayBox = AABB(pos).inflate(12.0)
        world.getEntitiesOfClass(Allay::class.java, allayBox).forEach { allay ->
            if (allay.isDancing) {
                allay.setJukeboxPlaying(pos, false)
            }
        }

        val parrotBox = AABB(pos).inflate(4.5)
        val nearbyParrots = world.getEntitiesOfClass(Parrot::class.java, parrotBox)
        if (nearbyParrots.isNotEmpty()) {
            nearbyParrots.forEach { it.setRecordPlayingNearby(pos, false) }
            world.levelEvent(null, 1011, pos, 0)
        }
    }

    private fun stopPlaying(world: ServerLevel, pos: BlockPos, discId: String) {
        val soundId = Identifier.fromNamespaceAndPath("peyajcustomdisc", "disc.${CustomDisc.cleanId(discId)}")
        val packet = ClientboundStopSoundPacket(soundId, SoundSource.RECORDS)
        world.server.playerList.players.forEach { p ->
            p.connection.send(packet)
        }
    }
}

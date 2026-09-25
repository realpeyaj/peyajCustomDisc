package com.peyaj.jukeboxweb.region

import com.peyaj.jukeboxweb.PeyajCustomDisc
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class RegionMusic(
    val discId: String,
    val loop: Boolean = true,
    val stereo: Boolean = true
)

// Manages region-based music playback using WorldGuard
class RegionMusicManager(private val plugin: PeyajCustomDisc) : Listener {

    // Tracks which region each player is currently in (for music purposes)
    private val playerCurrentRegion = ConcurrentHashMap<UUID, String>()
    
    // Tracks which disc is currently playing for each player
    private val playerCurrentDisc = ConcurrentHashMap<UUID, String>()

    // Tracks when music started playing for each player (for looping)
    private val playerMusicStartTime = ConcurrentHashMap<UUID, Long>()
    
    // Cache region -> RegionMusic mappings
    private var regionMusicMap: Map<String, RegionMusic> = emptyMap()
    
    // Looping task ID
    private var loopTaskId: Int = -1
    
    // Tracks last region check timestamp per player to throttle spatial queries
    private val lastCheckTime = ConcurrentHashMap<UUID, Long>()
    
    init {
        reload()
    }
    
    fun reload() {
        val previousMap = regionMusicMap
        val section = plugin.config.getConfigurationSection("region-music")
        if (section != null) {
            val newMap = mutableMapOf<String, RegionMusic>()
            var needsPackRebuild = false

            for (key in section.getKeys(false)) {
                val region = key.lowercase()
                if (section.isConfigurationSection(key)) {
                    val sub = section.getConfigurationSection(key)
                    val discId = sub?.getString("disc") ?: ""
                    val loop = sub?.getBoolean("loop", true) ?: true
                    val stereo = sub?.getBoolean("stereo", true) ?: true
                    if (discId.isNotBlank()) {
                        newMap[region] = RegionMusic(discId, loop, stereo)
                        if (stereo && ensureStereoFileExists(discId)) {
                            needsPackRebuild = true
                        }
                    }
                } else {
                    // Backwards-compatible flat string format: region_name: "disc_id" (defaults to loop: true, stereo: true)
                    val discId = section.getString(key) ?: ""
                    if (discId.isNotBlank()) {
                        newMap[region] = RegionMusic(discId, loop = true, stereo = true)
                        if (ensureStereoFileExists(discId)) {
                            needsPackRebuild = true
                        }
                    }
                }
            }
            regionMusicMap = newMap
            plugin.logger.info("Loaded ${regionMusicMap.size} region-music mappings.")

            if (needsPackRebuild) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    plugin.packGenerator.buildResourcePack(plugin.discManager.getAllDiscs())
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        com.peyaj.jukeboxweb.pack.PackUpdater.updateAllPlayers(plugin)
                    })
                })
            }
        } else {
            regionMusicMap = emptyMap()
        }

        // Stop or update music for players in regions that were removed or changed
        playerCurrentRegion.forEach { (uuid, region) ->
            val player = plugin.server.getPlayer(uuid) ?: return@forEach
            val newEntry = regionMusicMap[region]
            val oldDiscId = playerCurrentDisc[uuid] ?: previousMap[region]?.discId
            if (newEntry == null) {
                // Region was removed! Stop music immediately
                if (oldDiscId != null) {
                    stopMusicForPlayer(player, oldDiscId)
                }
                playerCurrentDisc.remove(uuid)
                playerCurrentRegion.remove(uuid)
                playerMusicStartTime.remove(uuid)
            } else if (oldDiscId != null && oldDiscId != newEntry.discId) {
                // Disc was changed! Stop old music and play new music
                stopMusicForPlayer(player, oldDiscId)
                playMusicForPlayer(player, newEntry, showActionBar = true)
            }
        }
        
        // Cancel existing loop task and start new one
        if (loopTaskId != -1) {
            plugin.server.scheduler.cancelTask(loopTaskId)
        }
        
        // Start looping checker (every 20 ticks = 1 second)
        loopTaskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkMusicLooping()
        }, 20L, 20L).taskId
    }

    fun removeRegion(region: String) {
        val regionLower = region.lowercase()
        val entry = regionMusicMap[regionLower]
        val configuredDiscId = entry?.discId ?: run {
            val section = plugin.config.getConfigurationSection("region-music")
            var found: String? = null
            if (section != null) {
                for (key in section.getKeys(false)) {
                    if (key.equals(regionLower, ignoreCase = true)) {
                        found = if (section.isConfigurationSection(key)) {
                            section.getString("$key.disc")
                        } else {
                            section.getString(key)
                        }
                        break
                    }
                }
            }
            found
        }

        playerCurrentRegion.forEach { (uuid, r) ->
            if (r.equals(regionLower, ignoreCase = true)) {
                val player = plugin.server.getPlayer(uuid)
                val discIdToStop = playerCurrentDisc.remove(uuid) ?: configuredDiscId
                if (player != null && discIdToStop != null) {
                    stopMusicForPlayer(player, discIdToStop)
                }
                playerCurrentRegion.remove(uuid)
                playerMusicStartTime.remove(uuid)
            }
        }

        // Also check any online players physically inside this WorldGuard region
        if (configuredDiscId != null) {
            try {
                val regionContainer = WorldGuard.getInstance().platform.regionContainer
                val query = regionContainer.createQuery()
                for (player in plugin.server.onlinePlayers) {
                    val loc = BukkitAdapter.adapt(player.location)
                    val regions = query.getApplicableRegions(loc)
                    if (regions.regions.any { it.id.equals(regionLower, ignoreCase = true) }) {
                        val discIdToStop = playerCurrentDisc.remove(player.uniqueId) ?: configuredDiscId
                        stopMusicForPlayer(player, discIdToStop)
                        playerCurrentRegion.remove(player.uniqueId)
                        playerMusicStartTime.remove(player.uniqueId)
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    fun disable() {
        if (loopTaskId != -1) {
            plugin.server.scheduler.cancelTask(loopTaskId)
            loopTaskId = -1
        }
        playerCurrentRegion.forEach { (uuid, region) ->
            val player = plugin.server.getPlayer(uuid)
            val discId = playerCurrentDisc.remove(uuid) ?: regionMusicMap[region]?.discId
            if (player != null && discId != null) {
                stopMusicForPlayer(player, discId)
            }
        }
        playerCurrentRegion.clear()
        playerCurrentDisc.clear()
        playerMusicStartTime.clear()
        lastCheckTime.clear()
    }

    private fun isBedrock(player: Player): Boolean {
        val uuid = player.uniqueId

        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") ||
            Bukkit.getPluginManager().isPluginEnabled("Geyser-Paper") ||
            Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
            try {
                val apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
                val api = apiClass.getMethod("api").invoke(null)
                val isBedrock = apiClass.getMethod("isBedrockPlayer", UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }

        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                val api = apiClass.getMethod("getInstance").invoke(null)
                val isBedrock = apiClass.getMethod("isFloodgatePlayer", UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }

        return uuid.mostSignificantBits == 0L
    }

    // Ensures discs/<id>_stereo.ogg exists for stereo playback, converting from mp3 or ogg if necessary
    fun ensureStereoFileExists(discId: String): Boolean {
        val discCleanId = discId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val stereoOgg = File(plugin.dataFolder, "discs/${discId}_stereo.ogg")
        val cleanStereoOgg = File(plugin.dataFolder, "discs/${discCleanId}_stereo.ogg")
        if (stereoOgg.exists() || cleanStereoOgg.exists()) return false

        val mp3 = File(plugin.dataFolder, "discs/$discId.mp3")
        val ogg = File(plugin.dataFolder, "discs/$discId.ogg")
        val cleanMp3 = File(plugin.dataFolder, "discs/$discCleanId.mp3")
        val cleanOgg = File(plugin.dataFolder, "discs/$discCleanId.ogg")

        if (!com.peyaj.jukeboxweb.util.AudioConverter.isFFmpegAvailable()) {
            return false
        }

        val sourceFile = if (mp3.exists()) mp3 else if (cleanMp3.exists()) cleanMp3 else if (ogg.exists()) ogg else if (cleanOgg.exists()) cleanOgg else null ?: return false
        val (success, _) = com.peyaj.jukeboxweb.util.AudioConverter.convertToOggStereo(sourceFile, cleanStereoOgg)
        return success
    }
    
    private fun checkMusicLooping() {
        val now = System.currentTimeMillis()
        
        playerCurrentRegion.forEach { (uuid, regionName) ->
            val player = plugin.server.getPlayer(uuid) ?: return@forEach
            val entry = regionMusicMap[regionName] ?: return@forEach
            if (!entry.loop) return@forEach // Looping disabled for this region; play once only

            val disc = plugin.discManager.getDisc(entry.discId) ?: return@forEach
            
            // Check if duration is set and has elapsed
            val duration = if (disc.durationSeconds > 0) {
                disc.durationSeconds
            } else {
                val oggFile = File(plugin.dataFolder, "discs/${disc.id}.ogg")
                val detected = com.peyaj.jukeboxweb.util.AudioConverter.getAudioDuration(oggFile)
                if (detected > 0) {
                    plugin.discManager.addDisc(disc.copy(durationSeconds = detected))
                    detected
                } else {
                    180 // Fallback to 3 minutes if unable to detect
                }
            }

            val startTime = playerMusicStartTime[uuid] ?: return@forEach
            val elapsed = (now - startTime) / 1000
            
            if (elapsed >= duration) {
                // Replay the music
                playMusicForPlayer(player, entry, showActionBar = false)
            }
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) {
            return // Same block, skip
        }
        
        // Throttle WorldGuard spatial queries to at most once every 500ms per player
        val now = System.currentTimeMillis()
        val last = lastCheckTime[event.player.uniqueId] ?: 0L
        if (now - last < 500L) {
            return
        }
        lastCheckTime[event.player.uniqueId] = now
        
        checkPlayerRegion(event.player)
    }
    
    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // Check after teleport completes
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            lastCheckTime[event.player.uniqueId] = System.currentTimeMillis()
            checkPlayerRegion(event.player)
        }, 2L)
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Check after 20 ticks (1s) to allow world loading and initial region detection
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                checkPlayerRegion(event.player)
            }
        }, 20L)
    }

    @EventHandler
    fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        if (event.status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            // Client has finished downloading and applying the resource pack
            // Delay 5 ticks to ensure client audio engine has registered sounds.json
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (event.player.isOnline) {
                    // Reset current region tracking to force music playback now that pack sounds are active
                    playerCurrentRegion.remove(event.player.uniqueId)
                    playerMusicStartTime.remove(event.player.uniqueId)
                    checkPlayerRegion(event.player)
                }
            }, 5L)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val currentRegion = playerCurrentRegion.remove(event.player.uniqueId)
        val discId = playerCurrentDisc.remove(event.player.uniqueId) ?: (if (currentRegion != null) regionMusicMap[currentRegion]?.discId else null)
        if (discId != null) {
            stopMusicForPlayer(event.player, discId)
        }
        playerMusicStartTime.remove(event.player.uniqueId)
        lastCheckTime.remove(event.player.uniqueId)
    }
    
    private fun checkPlayerRegion(player: Player) {
        if (regionMusicMap.isEmpty()) return
        
        try {
            val regionContainer = WorldGuard.getInstance().platform.regionContainer
            val query = regionContainer.createQuery()
            val loc = BukkitAdapter.adapt(player.location)
            val regions = query.getApplicableRegions(loc)
            
            // Find the first region that has music configured (priority order)
            var musicRegion: String? = null
            var musicEntry: RegionMusic? = null
            
            for (region in regions.regions) {
                val id = region.id.lowercase()
                val entry = regionMusicMap[id]
                if (entry != null) {
                    musicRegion = id
                    musicEntry = entry
                    break
                }
            }
            
            val currentRegion = playerCurrentRegion[player.uniqueId]
            
            if (musicRegion != currentRegion) {
                // Region changed!
                if (currentRegion != null) {
                    // Stop previous music
                    val prevDiscId = playerCurrentDisc.remove(player.uniqueId) ?: regionMusicMap[currentRegion]?.discId
                    if (prevDiscId != null) {
                        stopMusicForPlayer(player, prevDiscId)
                    }
                    playerMusicStartTime.remove(player.uniqueId)
                }
                
                if (musicRegion != null && musicEntry != null && musicEntry.discId.isNotEmpty()) {
                    // Start new music
                    playMusicForPlayer(player, musicEntry, showActionBar = true)
                    playerCurrentRegion[player.uniqueId] = musicRegion
                } else {
                    // Left all music regions
                    playerCurrentRegion.remove(player.uniqueId)
                    playerMusicStartTime.remove(player.uniqueId)
                    val remainingDisc = playerCurrentDisc.remove(player.uniqueId)
                    if (remainingDisc != null) {
                        stopMusicForPlayer(player, remainingDisc)
                    }
                }
            }
            
        } catch (e: Exception) {
            // WorldGuard not available or error - silently ignore
        }
    }
    
    private fun playMusicForPlayer(player: Player, entry: RegionMusic, showActionBar: Boolean = true) {
        val disc = plugin.discManager.getDisc(entry.discId)
        if (disc == null) {
            plugin.logger.warning("Region music disc '${entry.discId}' not found!")
            return
        }
        
        val discCleanId = entry.discId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val stereoFile = File(plugin.dataFolder, "discs/${discCleanId}_stereo.ogg")
        val altStereoFile = File(plugin.dataFolder, "discs/${entry.discId}_stereo.ogg")
        val hasStereo = entry.stereo && (stereoFile.exists() || altStereoFile.exists())

        val soundKeyName = if (hasStereo) "peyajcustomdisc:disc.${discCleanId}_stereo" else "peyajcustomdisc:disc.$discCleanId"
        val bedrockKeyName = if (hasStereo) "peyajcustomdisc.disc.${discCleanId}_stereo" else "peyajcustomdisc.disc.$discCleanId"
        val bedrockShortKey = if (hasStereo) "disc.${discCleanId}_stereo" else "disc.$discCleanId"

        if (isBedrock(player)) {
            // Single sound packet to Bedrock matching sound_definitions.json (is3D: false handles 2D stereo)
            player.playSound(player.location, soundKeyName, SoundCategory.RECORDS, 1.0f, 1.0f)
        } else {
            // Use player coordinates for both stereo and mono.
            // In OpenAL, 2-channel stereo audio is non-spatial by specification (plays in both ears without panning).
            // Mono audio will properly spatialize at the player's position.
            // Using player coordinates sends ClientboundSoundPacket, which avoids entity-emitter packet drops on Java clients.
            player.playSound(
                Sound.sound(Key.key(soundKeyName), Sound.Source.RECORD, 1.0f, 1.0f),
                player.location.x, player.location.y, player.location.z
            )
        }
        
        // Track playing disc and start time for looping
        playerCurrentDisc[player.uniqueId] = entry.discId
        playerMusicStartTime[player.uniqueId] = System.currentTimeMillis()
        
        // Show action bar (only on first play, not on loops)
        if (showActionBar) {
            val msg = Component.text("♫ Now Playing: ", NamedTextColor.GOLD)
                .append(Component.text(disc.name, NamedTextColor.AQUA))
                .append(Component.text(" by ", NamedTextColor.GRAY))
                .append(Component.text(disc.author, NamedTextColor.YELLOW))
            
            player.sendActionBar(msg)
        }
    }

    private fun stopMusicForPlayer(player: Player, discId: String) {
        val discCleanId = discId.lowercase().replace(Regex("[^a-z0-9_]"), "_")

        // Adventure stops (both default and RECORD source)
        try {
            player.stopSound(SoundStop.named(Key.key("peyajcustomdisc:disc.$discCleanId")))
            player.stopSound(SoundStop.named(Key.key("peyajcustomdisc:disc.${discCleanId}_stereo")))
            player.stopSound(SoundStop.namedOnSource(Key.key("peyajcustomdisc:disc.$discCleanId"), Sound.Source.RECORD))
            player.stopSound(SoundStop.namedOnSource(Key.key("peyajcustomdisc:disc.${discCleanId}_stereo"), Sound.Source.RECORD))
        } catch (ignored: Exception) {}

        // Bukkit stops (Java colon namespace)
        try {
            player.stopSound("peyajcustomdisc:disc.$discCleanId", SoundCategory.RECORDS)
            player.stopSound("peyajcustomdisc:disc.${discCleanId}_stereo", SoundCategory.RECORDS)
            player.stopSound("peyajcustomdisc:disc.$discCleanId")
            player.stopSound("peyajcustomdisc:disc.${discCleanId}_stereo")
        } catch (ignored: Exception) {}

        // Bedrock / Geyser stops (dot namespace)
        try {
            player.stopSound("peyajcustomdisc.disc.$discCleanId", SoundCategory.RECORDS)
            player.stopSound("disc.$discCleanId", SoundCategory.RECORDS)
            player.stopSound("peyajcustomdisc.disc.${discCleanId}_stereo", SoundCategory.RECORDS)
            player.stopSound("disc.${discCleanId}_stereo", SoundCategory.RECORDS)
        } catch (ignored: Exception) {}
    }

    fun getRegionMusic(region: String): RegionMusic? = regionMusicMap[region.lowercase()]

    fun getAllRegionMusic(): Map<String, RegionMusic> = Collections.unmodifiableMap(regionMusicMap)
}

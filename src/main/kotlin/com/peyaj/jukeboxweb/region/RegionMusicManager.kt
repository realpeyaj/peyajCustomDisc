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
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages region-based music playback using WorldGuard.
 * 
 * Config format:
 * ```yaml
 * region-music:
 *   region_name: "disc_id"
 *   spawn: "background_music"
 * ```
 */
class RegionMusicManager(private val plugin: PeyajCustomDisc) : Listener {

    // Tracks which region each player is currently in (for music purposes)
    private val playerCurrentRegion = ConcurrentHashMap<UUID, String>()
    
    // Tracks when music started playing for each player (for looping)
    private val playerMusicStartTime = ConcurrentHashMap<UUID, Long>()
    
    // Cache region -> discId mappings
    private var regionMusicMap: Map<String, String> = emptyMap()
    
    // Looping task ID
    private var loopTaskId: Int = -1
    
    init {
        reload()
    }
    
    fun reload() {
        val section = plugin.config.getConfigurationSection("region-music")
        if (section != null) {
            regionMusicMap = section.getKeys(false).associateWith { section.getString(it) ?: "" }
            plugin.logger.info("Loaded ${regionMusicMap.size} region-music mappings.")
        } else {
            regionMusicMap = emptyMap()
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
    
    private fun checkMusicLooping() {
        val now = System.currentTimeMillis()
        
        playerCurrentRegion.forEach { (uuid, regionName) ->
            val player = plugin.server.getPlayer(uuid) ?: return@forEach
            val discId = regionMusicMap[regionName] ?: return@forEach
            val disc = plugin.discManager.getDisc(discId) ?: return@forEach
            
            // Check if duration is set and has elapsed
            if (disc.durationSeconds > 0) {
                val startTime = playerMusicStartTime[uuid] ?: return@forEach
                val elapsed = (now - startTime) / 1000
                
                if (elapsed >= disc.durationSeconds) {
                    // Replay the music
                    playMusicForPlayer(player, discId, showActionBar = false)
                }
            }
        }
    }
    
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // Optimization: Only check if player moved to a new block
        val from = event.from
        val to = event.to ?: return
        
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) {
            return // Same block, skip
        }
        
        checkPlayerRegion(event.player)
    }
    
    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // Check after teleport completes
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            checkPlayerRegion(event.player)
        }, 2L)
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playerCurrentRegion.remove(event.player.uniqueId)
        playerMusicStartTime.remove(event.player.uniqueId)
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
            var discId: String? = null
            
            for (region in regions.regions) {
                val id = region.id.lowercase()
                if (regionMusicMap.containsKey(id)) {
                    musicRegion = id
                    discId = regionMusicMap[id]
                    break
                }
            }
            
            val currentRegion = playerCurrentRegion[player.uniqueId]
            
            if (musicRegion != currentRegion) {
                // Region changed!
                if (currentRegion != null) {
                    // Stop previous music
                    val prevDiscId = regionMusicMap[currentRegion]
                    if (prevDiscId != null) {
                        val key = Key.key("peyajcustomdisc:disc.$prevDiscId")
                        player.stopSound(SoundStop.named(key))
                    }
                    playerMusicStartTime.remove(player.uniqueId)
                }
                
                if (musicRegion != null && discId != null && discId.isNotEmpty()) {
                    // Start new music
                    playMusicForPlayer(player, discId, showActionBar = true)
                    playerCurrentRegion[player.uniqueId] = musicRegion
                } else {
                    // Left all music regions
                    playerCurrentRegion.remove(player.uniqueId)
                    playerMusicStartTime.remove(player.uniqueId)
                }
            }
            
        } catch (e: Exception) {
            // WorldGuard not available or error - silently ignore
        }
    }
    
    private fun playMusicForPlayer(player: Player, discId: String, showActionBar: Boolean = true) {
        val disc = plugin.discManager.getDisc(discId)
        if (disc == null) {
            plugin.logger.warning("Region music disc '$discId' not found!")
            return
        }
        
        // Play the sound
        val soundKey = Key.key("peyajcustomdisc:disc.$discId")
        player.playSound(
            Sound.sound(soundKey, Sound.Source.RECORD, 1.0f, 1.0f),
            player.location.x, player.location.y, player.location.z
        )
        
        // Track start time for looping
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
}

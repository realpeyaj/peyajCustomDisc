package com.peyaj.jukeboxweb.listener

import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Jukebox
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.entity.EntityType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import org.bukkit.entity.Player

class JukeboxListener(private val plugin: PeyajCustomDisc) : Listener {

    private data class PlayingSession(
        val taskId: Int, 
        val holoJavaUuid: UUID,
        val holoBedrockUuid: UUID,
        var startTime: Long,
        val durationSeconds: Int,
        val name: String,
        val author: String,
        val discId: String
    )
    private val activeJukeboxes = mutableMapOf<Location, PlayingSession>()
    private val loopingJukeboxes = mutableSetOf<Location>()

    fun disable() {
        activeJukeboxes.keys.toList().forEach { stopVisuals(it) }
    }

    private fun isBedrock(player: Player): Boolean {
        // Reflection-based Geyser check
        if (!Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) return false
        return try {
            val apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
            val api = apiClass.getMethod("api").invoke(null)
            apiClass.getMethod("isBedrockPlayer", UUID::class.java).invoke(api, player.uniqueId) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val bedrock = isBedrock(event.player)
        activeJukeboxes.values.forEach { session ->
            val javaHolo = Bukkit.getEntity(session.holoJavaUuid)
            val bedrockHolo = Bukkit.getEntity(session.holoBedrockUuid)
            
            if (bedrock) {
                javaHolo?.let { event.player.hideEntity(plugin, it) }
            } else {
                bedrockHolo?.let { event.player.hideEntity(plugin, it) }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    fun onShiftToggleLoop(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!event.player.isSneaking) return 
        if (event.hand != EquipmentSlot.HAND) return
        
        val block = event.clickedBlock ?: return
        if (block.type != Material.JUKEBOX) return
        
        val loc = block.location
        if (loopingJukeboxes.contains(loc)) {
            loopingJukeboxes.remove(loc)
            event.player.sendActionBar(Component.text("Looping: DISABLED", NamedTextColor.RED))
            event.player.playSound(loc, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
        } else {
            loopingJukeboxes.add(loc)
            event.player.sendActionBar(Component.text("Looping: ENABLED [∞]", NamedTextColor.LIGHT_PURPLE))
            event.player.playSound(loc, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f)
        }
        
        val session = activeJukeboxes[loc]
        if (session != null) {
            updateHolograms(loc, session.holoJavaUuid, session.holoBedrockUuid)
        }
        
        event.isCancelled = true 
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onJukeboxInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.player.isSneaking) return 
        if (event.hand != EquipmentSlot.HAND) return
        
        val block = event.clickedBlock ?: return
        if (block.type != Material.JUKEBOX) return
        
        plugin.server.scheduler.runTask(plugin, Runnable {
            val jukebox = block.state as Jukebox
            
            // Check if jukebox HAD a disc before (we're removing it) or is receiving a new one
            val previousSession = activeJukeboxes[block.location]
            
            if (jukebox.hasRecord()) {
                val record = jukebox.record
                
                // Stop EXISTING session at this location (visuals + sound if custom disc)
                if (previousSession != null) {
                    stopMusicAtLocationWithSound(block.location, previousSession.discId)
                }
                
                // Stop the specific vanilla sound only (not ALL record sounds!)
                val vanillaSound = getVanillaSound(record.type)
                if (vanillaSound != null) {
                    block.world.getNearbyPlayers(block.location, 64.0).forEach { p ->
                        p.stopSound(net.kyori.adventure.sound.SoundStop.named(vanillaSound))
                    }
                }
                
                val discId = plugin.discManager.getDiscIdFromItem(record)
                
                if (discId != null) {
                    val soundKey = "peyajcustomdisc:disc.$discId"
                    block.world.playSound(
                        Sound.sound(Key.key(soundKey), Sound.Source.RECORD, 1.0f, 1.0f),
                        block.location.x, block.location.y, block.location.z
                    )
                    
                    val disc = plugin.discManager.getDisc(discId)
                    if (disc != null) {
                        val msg = Component.text("Now Playing: ${disc.name}", NamedTextColor.AQUA)
                        
                        block.world.getNearbyPlayers(block.location, 64.0).forEach { p ->
                            p.sendActionBar(msg)
                        }
                        event.player.sendMessage(Component.text("Tip: Shift-Right-Click the Jukebox to toggle Loop Mode [∞]", NamedTextColor.GRAY))
                        
                        startVisuals(block.location, disc.name, disc.author, disc.durationSeconds, discId)
                    }
                }
            } else {
                // Jukebox is now EMPTY - disc was ejected! Stop the music.
                if (previousSession != null) {
                    stopMusicAtLocationWithSound(block.location, previousSession.discId)
                }
            }
        })
    }
    
    // Helper to map Disc Material -> Sound Key
    private fun getVanillaSound(mat: Material): Key? {
        val id = when(mat) {
            Material.MUSIC_DISC_13 -> "music_disc.13"
            Material.MUSIC_DISC_CAT -> "music_disc.cat"
            Material.MUSIC_DISC_BLOCKS -> "music_disc.blocks"
            Material.MUSIC_DISC_CHIRP -> "music_disc.chirp"
            Material.MUSIC_DISC_FAR -> "music_disc.far"
            Material.MUSIC_DISC_MALL -> "music_disc.mall"
            Material.MUSIC_DISC_MELLOHI -> "music_disc.mellohi"
            Material.MUSIC_DISC_STAL -> "music_disc.stal"
            Material.MUSIC_DISC_STRAD -> "music_disc.strad"
            Material.MUSIC_DISC_WARD -> "music_disc.ward"
            Material.MUSIC_DISC_11 -> "music_disc.11"
            Material.MUSIC_DISC_WAIT -> "music_disc.wait"
            Material.MUSIC_DISC_OTHERSIDE -> "music_disc.otherside"
            Material.MUSIC_DISC_5 -> "music_disc.5"
            Material.MUSIC_DISC_PIGSTEP -> "music_disc.pigstep"
            Material.MUSIC_DISC_RELIC -> "music_disc.relic"
            Material.MUSIC_DISC_CREATOR -> "music_disc.creator"
            Material.MUSIC_DISC_PRECIPICE -> "music_disc.precipice"
            else -> null
        }
        return if (id != null) Key.key("minecraft", id) else null
    }
    
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: org.bukkit.event.block.BlockBreakEvent) {
        if (event.block.type == Material.JUKEBOX) {
            val session = activeJukeboxes[event.block.location]
            if (session != null) {
                stopMusicAtLocationWithSound(event.block.location, session.discId)
            }
        }
    }
    
    private fun stopMusicAtLocation(location: Location) {
        val session = activeJukeboxes[location] ?: return
        stopVisuals(location)
    }
    
    private fun stopMusicAtLocationWithSound(location: Location, discId: String) {
        val world = location.world ?: return
        
        // Stop the custom disc sound for players near THIS jukebox
        val soundKey = Key.key("peyajcustomdisc:disc.$discId")
        world.getNearbyPlayers(location, 64.0).forEach { p ->
            p.stopSound(net.kyori.adventure.sound.SoundStop.named(soundKey))
        }
        
        stopVisuals(location)
    }

    private fun startVisuals(location: Location, discName: String, author: String, duration: Int, discId: String) {
        stopVisuals(location) // Safety cleanup

        val world = location.world ?: return
        if (!location.isChunkLoaded) return 

        // Spawn Java Hologram (1.2)
        val javaLoc = location.clone().add(0.5, 1.2, 0.5) 
        val holoJava = world.spawnEntity(javaLoc, EntityType.TEXT_DISPLAY) as TextDisplay
        configureHologram(holoJava, discName, author, loopingJukeboxes.contains(location))
        
        // Spawn Bedrock Hologram (1.5)
        val bedrockLoc = location.clone().add(0.5, 1.5, 0.5) 
        val holoBedrock = world.spawnEntity(bedrockLoc, EntityType.TEXT_DISPLAY) as TextDisplay
        configureHologram(holoBedrock, discName, author, loopingJukeboxes.contains(location))
        
        // Setup Visibility
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p.world == world) {
                 if (isBedrock(p)) {
                     p.hideEntity(plugin, holoJava)
                 } else {
                     p.hideEntity(plugin, holoBedrock)
                 }
            }
        }

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            // Kill only if block changed or explicit stop
            if (location.isChunkLoaded && location.block.type != Material.JUKEBOX) {
                stopMusicAtLocation(location)
                return@Runnable
            }
            
            // If chunk unloaded, just keep tracking time virtually, don't spawn particles
            if (!location.isChunkLoaded) {
                 return@Runnable
            }
            
            // Check/Respawn Visuals (in case they died on unload)
            val session = activeJukeboxes[location]
            if (session != null) {
                if (plugin.server.getEntity(session.holoJavaUuid) == null || plugin.server.getEntity(session.holoBedrockUuid) == null) {
                     // Respawn logic
                     startVisuals(location, session.name, session.author, session.durationSeconds, session.discId)
                     return@Runnable // Restarted visuals, exit this tick
                }
            
                if (session.durationSeconds > 0) {
                     val elapsed = (System.currentTimeMillis() - session.startTime) / 1000.0
                     if (elapsed >= session.durationSeconds) {
                         if (loopingJukeboxes.contains(location)) {
                             // Loop logic
                             // Stop previous iteration to be safe
                             val key = Key.key("peyajcustomdisc:disc.$discId")
                             world.stopSound(net.kyori.adventure.sound.SoundStop.named(key))
                             
                             world.playSound(
                                Sound.sound(key, Sound.Source.RECORD, 1.0f, 1.0f),
                                location.x, location.y, location.z
                             )
                             session.startTime = System.currentTimeMillis()
                         } else {
                             stopMusicAtLocation(location)
                             return@Runnable
                         }
                     }
                }
            }
            
            // Note Particle
            val note = (0..24).random() / 24.0 
            world.spawnParticle(Particle.NOTE, location.clone().add(0.5, 0.8, 0.5), 0, note, 0.0, 0.0, 1.0)
            
        }, 0L, 10L)

        activeJukeboxes[location] = PlayingSession(task.taskId, holoJava.uniqueId, holoBedrock.uniqueId, System.currentTimeMillis(), duration, discName, author, discId)
    }
    
    private fun configureHologram(hologram: TextDisplay, discName: String, author: String, looping: Boolean) {
        updateHologramText(hologram, discName, author, looping)
        hologram.billboard = Display.Billboard.CENTER
        hologram.isSeeThrough = false
        hologram.transformation = hologram.transformation.apply { scale.set(0.5f, 0.5f, 0.5f) }
        hologram.isPersistent = false
        hologram.addScoreboardTag("peyaj_jukebox_display")
    }

    private fun updateHolograms(location: Location, javaUuid: UUID, bedrockUuid: UUID) {
        val world = location.world ?: return
        val session = activeJukeboxes[location] ?: return
        val looping = loopingJukeboxes.contains(location)
        
        val javaHolo = world.getEntity(javaUuid) as? TextDisplay
        if (javaHolo != null) updateHologramText(javaHolo, session.name, session.author, looping)
        
        val bedrockHolo = world.getEntity(bedrockUuid) as? TextDisplay
        if (bedrockHolo != null) updateHologramText(bedrockHolo, session.name, session.author, looping)
    }
    
    private fun updateHologramText(hologram: TextDisplay, discName: String, author: String, looping: Boolean) {
        val loopText = if (looping) " [∞]" else ""
        val color = if (looping) NamedTextColor.LIGHT_PURPLE else NamedTextColor.GOLD
        
        hologram.text(
            Component.text("♫ Now Playing$loopText ♫\n", color)
            .append(Component.text("$discName\n", NamedTextColor.AQUA))
            .append(Component.text("by $author", NamedTextColor.YELLOW))
        )
    }

    private fun stopVisuals(location: Location) {
        val session = activeJukeboxes.remove(location) ?: return
        plugin.server.scheduler.cancelTask(session.taskId)
        
        val world = location.world
        world?.getEntity(session.holoJavaUuid)?.remove()
        world?.getEntity(session.holoBedrockUuid)?.remove()
    }
}

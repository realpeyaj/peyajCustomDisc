package com.peyaj.jukeboxweb.listener

import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.SoundCategory
import org.bukkit.block.Jukebox
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

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
    private val interactCooldown = mutableMapOf<UUID, Long>()

    fun disable() {
        activeJukeboxes.keys.toList().forEach { stopVisuals(it) }
    }

    private fun isBedrock(player: Player): Boolean {
        val uuid = player.uniqueId

        // 1. Geyser API Check
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

        // 2. Floodgate API Check
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                val api = apiClass.getMethod("getInstance").invoke(null)
                val isBedrock = apiClass.getMethod("isFloodgatePlayer", UUID::class.java).invoke(api, uuid) as Boolean
                if (isBedrock) return true
            } catch (ignored: Exception) {}
        }

        // 3. Fallback check: Floodgate UUIDs have 0L mostSignificantBits
        return uuid.mostSignificantBits == 0L
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

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onJukeboxInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.player.isSneaking) return 
        
        val block = event.clickedBlock ?: return
        if (block.type != Material.JUKEBOX) return

        val now = System.currentTimeMillis()
        if (now - (interactCooldown[event.player.uniqueId] ?: 0L) < 50L) {
            return
        }

        // Ignore double trigger on OFF_HAND if main hand holds a custom disc
        if (event.hand == EquipmentSlot.OFF_HAND) {
            val mainHand = event.player.inventory.itemInMainHand
            if (plugin.discManager.isCustomDisc(mainHand)) return
        }
        
        val itemInHand = event.item 
            ?: (if (event.hand == EquipmentSlot.OFF_HAND) event.player.inventory.itemInOffHand else event.player.inventory.itemInMainHand)

        val jukebox = block.state as Jukebox
        val activeSession = activeJukeboxes[block.location]
        
        var existingRecord = try {
            val item = jukebox.inventory.getItem(0)
            if (item != null && item.type != Material.AIR) item else null
        } catch (e: Exception) {
            try { if (jukebox.hasRecord()) jukebox.record else null } catch (ignored: Exception) { null }
        }
        
        // If block state is somehow empty but we have an active session, reconstruct it
        if (existingRecord == null && activeSession != null) {
            existingRecord = plugin.discManager.createDiscItem(activeSession.discId)
        }

        val existingDiscId = plugin.discManager.getDiscIdFromItem(existingRecord)
        val holdingDiscId = plugin.discManager.getDiscIdFromItem(itemInHand)

        // CASE 1: Jukebox contains a CUSTOM disc -> Eject it or swap it!
        if (existingDiscId != null) {
            interactCooldown[event.player.uniqueId] = now
            event.isCancelled = true // Cancel vanilla interaction to prevent double action bar & vanilla sounds!
            
            loopingJukeboxes.remove(block.location)

            // Stop existing custom session & remove holograms
            stopMusicAtLocationWithSound(block.location, existingDiscId)

            // Drop the custom disc item into the world
            block.world.dropItemNaturally(block.location.clone().add(0.5, 1.0, 0.5), existingRecord!!)
            
            // Clear jukebox record
            try {
                jukebox.inventory.clear()
            } catch (e: Exception) {
                try { jukebox.setRecord(null) } catch (ignored: Exception) {}
            }
            jukebox.update(true, false)

            // FIX: If the player clicked with a vanilla disc in hand, Bedrock (Geyser) might predict its insertion and play it.
            // Since we cancelled the event to pop out the custom disc, we MUST forcefully stop the vanilla disc sound client-side.
            if (itemInHand.type.isRecord) {
                val material = itemInHand.type
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    stopVanillaDiscSounds(block.world, block.location, material)
                }, 2L)
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    stopVanillaDiscSounds(block.world, block.location, material)
                }, 5L)
            }

            return
        }

        // CASE 2: Jukebox is EMPTY, and player is holding a CUSTOM disc -> Insert it!
        if (holdingDiscId != null) {
            // If the jukebox already has ANY record (vanilla), let vanilla Minecraft eject it naturally!
            if (existingRecord != null && existingRecord.type != Material.AIR) {
                return // Do not cancel the event! Vanilla will eject the vanilla disc.
            }

            interactCooldown[event.player.uniqueId] = now
            event.isCancelled = true // Cancel vanilla interaction to prevent double action bar & vanilla sounds!
            insertAndPlayCustomDisc(event.player, block, jukebox, itemInHand, holdingDiscId, event.hand ?: EquipmentSlot.HAND)
            return
        }

        // CASE 3: Vanilla disc or empty hand on vanilla jukebox -> Let Minecraft handle it 100% natively!
    }

    private fun insertAndPlayCustomDisc(
        player: Player, 
        block: org.bukkit.block.Block, 
        jukebox: Jukebox, 
        item: org.bukkit.inventory.ItemStack, 
        discId: String,
        hand: EquipmentSlot
    ) {
        // 1. Clone 1 item to put inside Jukebox
        val placedItem = item.clone().apply { amount = 1 }
        try {
            jukebox.inventory.setItem(0, placedItem)
        } catch (e: Exception) {
            try { jukebox.setRecord(placedItem) } catch (ignored: Exception) {}
        }
        jukebox.update(true, false) // applyPhysics = false prevents native Minecraft sound triggers!
        
        try {
            jukebox.stopPlaying()
        } catch (ignored: Exception) {}

        // 2. Consume 1 item from player inventory (if not Creative)
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
            if (hand == EquipmentSlot.OFF_HAND) {
                val offHandItem = player.inventory.itemInOffHand
                offHandItem.amount = offHandItem.amount - 1
                player.inventory.setItemInOffHand(offHandItem)
            } else {
                val mainHandItem = player.inventory.itemInMainHand
                mainHandItem.amount = mainHandItem.amount - 1
                player.inventory.setItemInMainHand(mainHandItem)
            }
        }

        // 3. Stop ANY vanilla record sound by explicitly stopping the item's sound string
        // Geyser requires the explicit string to send a Bedrock StopSound packet!
        // We send it multiple times over a few ticks to account for Geyser interact latency
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            try { (block.state as? Jukebox)?.stopPlaying() } catch (ignored: Exception) {}
            stopVanillaDiscSounds(block.world, block.location, item.type)
        }, 1L)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            try { (block.state as? Jukebox)?.stopPlaying() } catch (ignored: Exception) {}
            stopVanillaDiscSounds(block.world, block.location, item.type)
        }, 2L)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            try { (block.state as? Jukebox)?.stopPlaying() } catch (ignored: Exception) {}
            stopVanillaDiscSounds(block.world, block.location, item.type)
        }, 4L)

        // 4. Play custom disc music 5 ticks later so the stop packets are fully processed first
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            playCustomDiscSound(block.world, block.location, discId)
        }, 5L)

        // 5. Action bar + holograms
        val disc = plugin.discManager.getDisc(discId)
        if (disc != null) {
            val msg = Component.text("Now Playing: ${disc.name}", NamedTextColor.AQUA)
            block.world.getNearbyPlayers(block.location, getJukeboxRange()).forEach { p ->
                p.sendActionBar(msg)
            }
            player.sendMessage(Component.text("Tip: Shift-Right-Click the Jukebox to toggle Loop Mode [∞]", NamedTextColor.GRAY))
            startVisuals(block.location, disc.name, disc.author, disc.durationSeconds, discId)
        }
    }
    
    // Helper to explicitly stop Java and Bedrock vanilla record sound keys for nearby players
    private fun stopVanillaDiscSounds(world: org.bukkit.World, location: Location, mat: Material) {
        val style = when(mat) {
            Material.MUSIC_DISC_13 -> "13"
            Material.MUSIC_DISC_CAT -> "cat"
            Material.MUSIC_DISC_BLOCKS -> "blocks"
            Material.MUSIC_DISC_CHIRP -> "chirp"
            Material.MUSIC_DISC_FAR -> "far"
            Material.MUSIC_DISC_MALL -> "mall"
            Material.MUSIC_DISC_MELLOHI -> "mellohi"
            Material.MUSIC_DISC_STAL -> "stal"
            Material.MUSIC_DISC_STRAD -> "strad"
            Material.MUSIC_DISC_WARD -> "ward"
            Material.MUSIC_DISC_11 -> "11"
            Material.MUSIC_DISC_WAIT -> "wait"
            Material.MUSIC_DISC_OTHERSIDE -> "otherside"
            Material.MUSIC_DISC_5 -> "5"
            Material.MUSIC_DISC_PIGSTEP -> "pigstep"
            Material.MUSIC_DISC_RELIC -> "relic"
            Material.MUSIC_DISC_CREATOR -> "creator"
            Material.MUSIC_DISC_PRECIPICE -> "precipice"
            Material.MUSIC_DISC_CREATOR_MUSIC_BOX -> "creator_music_box"
            Material.MUSIC_DISC_TEARS -> "tears"
            Material.MUSIC_DISC_LAVA_CHICKEN -> "lava_chicken"
            else -> null
        } ?: return

        val range = getJukeboxRange()
        val soundKeys = listOf(
            "minecraft:music_disc.$style",
            "music_disc.$style",
            "minecraft:records.$style",
            "records.$style",
            "minecraft:record.$style",
            "record.$style"
        )

        world.getNearbyPlayers(location, range).forEach { p ->
            for (key in soundKeys) {
                p.stopSound(key, SoundCategory.RECORDS)
            }
        }
    }
    
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: org.bukkit.event.block.BlockBreakEvent) {
        if (event.block.type == Material.JUKEBOX) {
            handleJukeboxBreak(event.block)
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: org.bukkit.event.entity.EntityExplodeEvent) {
        event.blockList().toList().forEach { block ->
            if (block.type == Material.JUKEBOX) {
                handleJukeboxBreak(block)
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: org.bukkit.event.block.BlockExplodeEvent) {
        event.blockList().toList().forEach { block ->
            if (block.type == Material.JUKEBOX) {
                handleJukeboxBreak(block)
            }
        }
    }

    private fun handleJukeboxBreak(block: org.bukkit.block.Block) {
        loopingJukeboxes.remove(block.location)
        val session = activeJukeboxes[block.location]
        if (session != null) {
            // Drop the custom disc item natively
            val discItem = plugin.discManager.createDiscItem(session.discId)
            if (discItem != null) {
                block.world.dropItemNaturally(block.location.clone().add(0.5, 0.5, 0.5), discItem)
            }
            
            // Clear the jukebox so vanilla doesn't drop anything else
            val state = block.state as? Jukebox
            if (state != null) {
                try { state.inventory.clear() } catch (ignored: Exception) {}
                try { state.setRecord(null) } catch (ignored: Exception) {}
                state.update(true, false)
            }
            
            stopMusicAtLocationWithSound(block.location, session.discId)
        }
    }
    
    private fun stopMusicAtLocationWithSound(location: Location, discId: String) {
        val world = location.world ?: return
        
        // Dual-path stop: Adventure API for Java, /stopsound for Bedrock
        stopCustomDiscSound(world, location, discId)
        
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
                val session = activeJukeboxes[location]
                if (session != null) {
                    stopMusicAtLocationWithSound(location, session.discId)
                }
                return@Runnable
            }
            
            // If chunk unloaded, just keep tracking time virtually, don't spawn particles
            if (!location.isChunkLoaded) {
                 return@Runnable
            }
                      // Check/Respawn Visuals (in case they died on unload)
            val session = activeJukeboxes[location]
            if (session != null) {
                val javaEntity = world.getEntity(session.holoJavaUuid)
                val bedrockEntity = world.getEntity(session.holoBedrockUuid)
                if (javaEntity == null || !javaEntity.isValid || bedrockEntity == null || !bedrockEntity.isValid) {
                     // Respawn logic
                     startVisuals(location, session.name, session.author, session.durationSeconds, session.discId)
                     return@Runnable // Restarted visuals, exit this tick
                }
            
                val duration = if (session.durationSeconds > 0) session.durationSeconds else 180
                val elapsed = (System.currentTimeMillis() - session.startTime) / 1000.0
                if (elapsed >= duration) {
                    if (loopingJukeboxes.contains(location)) {
                        // Loop logic - dual-path stop then play
                        stopCustomDiscSound(world, location, discId)
                        playCustomDiscSound(world, location, discId)
                        session.startTime = System.currentTimeMillis()
                    } else {
                        val autoEject = plugin.config.getBoolean("jukebox.auto-eject", true)
                        if (autoEject && location.isChunkLoaded && location.block.type == Material.JUKEBOX) {
                            val jb = location.block.state as? Jukebox
                            if (jb != null) {
                                val recordToDrop = try {
                                    val item = jb.inventory.getItem(0)
                                    if (item != null && item.type != Material.AIR) item else null
                                } catch (e: Exception) {
                                    try { if (jb.hasRecord()) jb.record else null } catch (ignored: Exception) { null }
                                } ?: plugin.discManager.createDiscItem(session.discId)

                                try {
                                    jb.inventory.clear()
                                } catch (e: Exception) {
                                    try { jb.setRecord(null) } catch (ignored: Exception) {}
                                }
                                jb.update(true, false)

                                if (recordToDrop != null) {
                                    world.dropItemNaturally(location.clone().add(0.5, 1.0, 0.5), recordToDrop)
                                }
                            }
                        }
                        loopingJukeboxes.remove(location)
                        stopMusicAtLocationWithSound(location, discId)
                        return@Runnable
                    }
                }
            }
            
            // Custom Particles (only spawn if players are within viewing range)
            if (plugin.config.getBoolean("jukebox.enable-particles", true)) {
                val range = getJukeboxRange().coerceAtMost(32.0)
                if (world.getNearbyPlayers(location, range).isNotEmpty()) {
                    val session = activeJukeboxes[location]
                    val discId = session?.discId ?: ""
                    spawnJukeboxParticle(world, location, discId)
                }
            }
            
        }, 0L, 10L)

        activeJukeboxes[location] = PlayingSession(task.taskId, holoJava.uniqueId, holoBedrock.uniqueId, System.currentTimeMillis(), duration, discName, author, discId)
    }
    
    private fun configureHologram(hologram: TextDisplay, discName: String, author: String, looping: Boolean) {
        updateHologramText(hologram, discName, author, looping)
        hologram.billboard = Display.Billboard.CENTER
        // Enforce non-see-through occlusion for Bedrock block clipping
        hologram.isDefaultBackground = false
        hologram.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
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

    private fun getJukeboxVolume(): Float = plugin.config.getDouble("jukebox.volume", 1.0).toFloat()
    private fun getJukeboxPitch(): Float = plugin.config.getDouble("jukebox.pitch", 1.0).toFloat()
    private fun getJukeboxRange(): Double = plugin.config.getDouble("jukebox.range", 64.0)

    // Sound playback — sends colon-separated key to Java players and dot-separated key to Bedrock players
    private fun playCustomDiscSound(world: org.bukkit.World, location: Location, discId: String) {
        val range = getJukeboxRange()
        val baseVolume = getJukeboxVolume()
        val pitch = getJukeboxPitch()
        val discCleanId = discId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val javaKey = "peyajcustomdisc:disc.$discId"
        val bedrockKey = "peyajcustomdisc.disc.$discCleanId"
        
        // Java relies on 'attenuation_distance' embedded in the resource pack, so volume just scales the base amplitude (usually 1.0)
        // This gives a perfect linear fade out to the configured range.
        val javaVolume = baseVolume
        
        // Bedrock uses volume to calculate falloff distance heavily (vol 1.0 = ~16 blocks)
        val bedrockVolume = baseVolume * (range / 16.0).toFloat()

        world.getNearbyPlayers(location, range).forEach { p ->
            if (isBedrock(p)) {
                p.playSound(location, bedrockKey, SoundCategory.RECORDS, bedrockVolume, pitch)
                p.playSound(location, "disc.$discCleanId", SoundCategory.RECORDS, bedrockVolume, pitch)
            } else {
                p.playSound(location, javaKey, SoundCategory.RECORDS, javaVolume, pitch)
            }
        }
    }

    // Sound stop — stops both Java and Bedrock sound keys for nearby players
    private fun stopCustomDiscSound(world: org.bukkit.World, location: Location, discId: String) {
        val range = getJukeboxRange()
        val discCleanId = discId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val javaKey = "peyajcustomdisc:disc.$discId"
        val bedrockKey = "peyajcustomdisc.disc.$discCleanId"

        world.getNearbyPlayers(location, range).forEach { p ->
            if (isBedrock(p)) {
                p.stopSound(bedrockKey, SoundCategory.RECORDS)
                p.stopSound("disc.$discCleanId", SoundCategory.RECORDS)
            } else {
                p.stopSound(javaKey, SoundCategory.RECORDS)
            }
        }
    }

    private fun spawnJukeboxParticle(world: org.bukkit.World, location: Location, discId: String) {
        val configType = plugin.config.getString("jukebox.particle-type", "MATCH") ?: "MATCH"
        val disc = plugin.discManager.getDisc(discId)
        val style = disc?.style?.lowercase() ?: "cat"
        
        val particleType = if (configType.equals("MATCH", ignoreCase = true)) {
            when (style) {
                "pigstep", "lava_chicken", "11" -> org.bukkit.Particle.FLAME
                "otherside", "5" -> org.bukkit.Particle.SOUL
                "creator", "relic" -> {
                    try {
                        org.bukkit.Particle.valueOf("ENCHANT")
                    } catch (e: Exception) {
                        try {
                            org.bukkit.Particle.valueOf("ENCHANTING_TABLE")
                        } catch (e2: Exception) {
                            org.bukkit.Particle.NOTE
                        }
                    }
                }
                "tears" -> org.bukkit.Particle.SPLASH
                else -> org.bukkit.Particle.NOTE
            }
        } else {
            try {
                org.bukkit.Particle.valueOf(configType.uppercase())
            } catch (e: Exception) {
                org.bukkit.Particle.NOTE
            }
        }

        val spawnLoc = location.clone().add(0.5, 1.2, 0.5)
        if (particleType == org.bukkit.Particle.NOTE) {
            val note = (0..24).random() / 24.0
            world.spawnParticle(org.bukkit.Particle.NOTE, spawnLoc, 0, note, 0.0, 0.0, 1.0)
        } else {
            try {
                if (particleType.name == "ENCHANT" || particleType.name == "ENCHANTING_TABLE") {
                    world.spawnParticle(particleType, spawnLoc, 3, 0.2, 0.2, 0.2, 0.1)
                } else {
                    world.spawnParticle(particleType, spawnLoc, 2, 0.15, 0.15, 0.15, 0.02)
                }
            } catch (e: Exception) {
                // Fallback
                val note = (0..24).random() / 24.0
                world.spawnParticle(org.bukkit.Particle.NOTE, spawnLoc, 0, note, 0.0, 0.0, 1.0)
            }
        }
    }
}

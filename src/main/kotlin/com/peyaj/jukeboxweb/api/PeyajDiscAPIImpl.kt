package com.peyaj.jukeboxweb.api

import com.peyaj.jukeboxweb.PeyajCustomDisc
import com.peyaj.jukeboxweb.disc.CustomDisc
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Implementation of PeyajDiscAPI.
 */
class PeyajDiscAPIImpl(private val plugin: PeyajCustomDisc) : PeyajDiscAPI {

    override fun playDisc(player: Player, discId: String): Boolean {
        val disc = plugin.discManager.getDisc(discId) ?: return false
        
        val soundKey = Key.key("peyajcustomdisc:disc.$discId")
        player.playSound(
            Sound.sound(soundKey, Sound.Source.RECORD, 1.0f, 1.0f),
            player.location.x, player.location.y, player.location.z
        )
        
        return true
    }
    
    override fun playDiscAt(player: Player, discId: String, x: Double, y: Double, z: Double): Boolean {
        val disc = plugin.discManager.getDisc(discId) ?: return false
        
        val soundKey = Key.key("peyajcustomdisc:disc.$discId")
        player.playSound(
            Sound.sound(soundKey, Sound.Source.RECORD, 1.0f, 1.0f),
            x, y, z
        )
        
        return true
    }
    
    override fun stopDisc(player: Player) {
        // Stop ALL sounds from RECORD source (custom discs, vanilla discs, jukebox music)
        // This is intentional for API users who want to clear all music for a player
        // Use stopDisc(player, discId) to stop a specific disc instead
        player.stopSound(SoundStop.source(Sound.Source.RECORD))
    }
    
    override fun stopDisc(player: Player, discId: String) {
        val key = Key.key("peyajcustomdisc:disc.$discId")
        player.stopSound(SoundStop.named(key))
    }
    
    override fun getDisc(discId: String): CustomDisc? {
        return plugin.discManager.getDisc(discId)
    }
    
    override fun getAllDiscs(): List<CustomDisc> {
        return plugin.discManager.getAllDiscs()
    }
    
    override fun createDiscItem(discId: String): ItemStack? {
        return plugin.discManager.createDiscItem(discId)
    }
    
    override fun isCustomDisc(item: ItemStack?): Boolean {
        return plugin.discManager.isCustomDisc(item)
    }
    
    override fun getDiscIdFromItem(item: ItemStack?): String? {
        return plugin.discManager.getDiscIdFromItem(item)
    }
    
    override fun sendNowPlayingActionBar(player: Player, discId: String) {
        val disc = plugin.discManager.getDisc(discId) ?: return
        
        val msg = Component.text("♫ Now Playing: ", NamedTextColor.GOLD)
            .append(Component.text(disc.name, NamedTextColor.AQUA))
            .append(Component.text(" by ", NamedTextColor.GRAY))
            .append(Component.text(disc.author, NamedTextColor.YELLOW))
        
        player.sendActionBar(msg)
    }
}

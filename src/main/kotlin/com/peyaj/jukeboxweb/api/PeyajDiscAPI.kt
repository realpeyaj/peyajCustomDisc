package com.peyaj.jukeboxweb.api

import com.peyaj.jukeboxweb.disc.CustomDisc
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Public API for peyajCustomDisc plugin.
 * 
 * Access via:
 * ```kotlin
 * val api = Bukkit.getServicesManager().getRegistration(PeyajDiscAPI::class.java)?.provider
 * api?.playDisc(player, "my_disc_id")
 * ```
 * 
 * Or in Java:
 * ```java
 * PeyajDiscAPI api = Bukkit.getServicesManager().getRegistration(PeyajDiscAPI.class).getProvider();
 * api.playDisc(player, "my_disc_id");
 * ```
 */
interface PeyajDiscAPI {

    /**
     * Play a custom disc for a specific player.
     * The player will hear the audio as if it's playing at their location.
     * 
     * @param player The player to play the disc for.
     * @param discId The ID of the disc to play.
     * @return true if the disc was found and started playing, false otherwise.
     */
    fun playDisc(player: Player, discId: String): Boolean
    
    /**
     * Play a custom disc for a specific player at a specific location.
     * The player will hear the audio as if it's playing at the given coordinates.
     * 
     * @param player The player to play the disc for.
     * @param discId The ID of the disc to play.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @param z Z coordinate.
     * @return true if the disc was found and started playing, false otherwise.
     */
    fun playDiscAt(player: Player, discId: String, x: Double, y: Double, z: Double): Boolean
    
    /**
     * Stop all custom disc sounds for a player.
     * 
     * @param player The player to stop sounds for.
     */
    fun stopDisc(player: Player)
    
    /**
     * Stop a specific custom disc sound for a player.
     * 
     * @param player The player to stop the sound for.
     * @param discId The ID of the disc to stop.
     */
    fun stopDisc(player: Player, discId: String)
    
    /**
     * Get a custom disc by its ID.
     * 
     * @param discId The ID of the disc.
     * @return The CustomDisc object, or null if not found.
     */
    fun getDisc(discId: String): CustomDisc?
    
    /**
     * Get all registered custom discs.
     * 
     * @return A list of all CustomDisc objects.
     */
    fun getAllDiscs(): List<CustomDisc>
    
    /**
     * Create an ItemStack representing a custom disc.
     * This item can be placed in jukeboxes.
     * 
     * @param discId The ID of the disc.
     * @return The ItemStack, or null if the disc ID is not found.
     */
    fun createDiscItem(discId: String): ItemStack?
    
    /**
     * Check if an ItemStack is a custom disc.
     * 
     * @param item The ItemStack to check.
     * @return true if it's a custom disc, false otherwise.
     */
    fun isCustomDisc(item: ItemStack?): Boolean
    
    /**
     * Get the disc ID from an ItemStack.
     * 
     * @param item The ItemStack to check.
     * @return The disc ID, or null if not a custom disc.
     */
    fun getDiscIdFromItem(item: ItemStack?): String?
    
    /**
     * Send an action bar message showing "Now Playing" info for a disc.
     * 
     * @param player The player to send the message to.
     * @param discId The disc ID.
     */
    fun sendNowPlayingActionBar(player: Player, discId: String)
}

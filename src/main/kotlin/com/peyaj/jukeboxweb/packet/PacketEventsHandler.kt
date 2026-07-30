package com.peyaj.jukeboxweb.packet

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect
import com.peyaj.jukeboxweb.PeyajCustomDisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Jukebox
import org.bukkit.entity.Player

class PacketEventsHandler(private val plugin: PeyajCustomDisc) : PacketListenerAbstract(PacketListenerPriority.HIGH) {

    companion object {
        fun init(plugin: PeyajCustomDisc) {
            if (Bukkit.getPluginManager().isPluginEnabled("PacketEvents") ||
                Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
                try {
                    PacketEvents.getAPI().eventManager.registerListener(PacketEventsHandler(plugin))
                    plugin.logger.info("✔ PacketEvents listener registered successfully.")
                } catch (e: Throwable) {
                    plugin.logger.warning("PacketEvents detected but failed to initialize listener: ${e.message}")
                }
            }
        }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType == PacketType.Play.Server.SOUND_EFFECT) {
            val wrapper = WrapperPlayServerSoundEffect(event)
            val soundName = wrapper.sound.name?.toString() ?: ""
            plugin.logger.info("DEBUG [SOUND_EFFECT]: $soundName")
            if (!soundName.contains("peyajcustomdisc") && (soundName.contains("music_disc.") || soundName.contains("records.") || soundName.contains("record."))) {
                event.isCancelled = true
            }
        } else if (event.packetType == PacketType.Play.Server.EFFECT) {
            val packet = WrapperPlayServerEffect(event)
            plugin.logger.info("DEBUG [EFFECT]: type=${packet.type}, data=${packet.data}")
            if (packet.type == 1010) {
                // Cancel native 1010 packet for jukebox play event (data != 0) to prevent vanilla double action bar & native Bedrock sound
                if (packet.data != 0) {
                    event.isCancelled = true
                }
  
                val player = event.getPlayer<Player>() ?: return
                val vec = packet.position.toVector3d()
                val loc = Location(player.world, vec.x, vec.y, vec.z)
                
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    val blockState = loc.block.state
                    if (blockState is Jukebox) {
                        val discId = plugin.discManager.getDiscIdFromItem(blockState.record)
                        if (discId != null) {
                            val disc = plugin.discManager.getDisc(discId)
                            if (disc != null) {
                                player.sendActionBar(Component.text("Now Playing: ♫ ${disc.author} - ${disc.name} ♫", NamedTextColor.GOLD))
                            }
                        }
                    }
                }, 1)
            }
        }
    }
}

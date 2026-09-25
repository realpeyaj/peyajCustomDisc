package com.peyaj.jukeboxweb.fabric.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.peyaj.jukeboxweb.auth.AuthManager
import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

object FabricJukeboxCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val discNode = literal("disc")
                .requires { source -> source.hasPermissionLevel(2) }

            // /disc web
            discNode.then(
                literal("web").executes { ctx ->
                    val source = ctx.source
                    val player = source.player ?: run {
                        source.sendMessage(Text.literal("This command must be executed by a player.").formatted(Formatting.RED))
                        return@executes 0
                    }

                    val token = AuthManager.createLoginToken(player.uuid)
                    val urlBase = PeyajCustomDiscFabric.config.publicUrl.trimEnd('/')
                    val webUrl = "$urlBase/login?token=$token"

                    source.sendMessage(Text.literal("Disc Creator Web Interface").formatted(Formatting.GOLD, Formatting.BOLD))
                    source.sendMessage(Text.literal("Click the link below to log in securely:").formatted(Formatting.GRAY))
                    
                    val link = Text.literal("▶ Click here to open Disc Creator")
                        .styled { style ->
                            style.withColor(Formatting.AQUA)
                                .withUnderline(true)
                                .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, webUrl))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Open $webUrl in your web browser")))
                        }
                    source.sendMessage(link)
                    source.sendMessage(Text.literal("Note: This link is one-time use.").formatted(Formatting.DARK_GRAY))
                    1
                }
            )

            // /disc reload
            discNode.then(
                literal("reload").executes { ctx ->
                    val source = ctx.source
                    PeyajCustomDiscFabric.reload()
                    source.sendMessage(Text.literal("✔ peyajCustomDisc configuration and WebServer reloaded!").formatted(Formatting.GREEN))
                    1
                }
            )

            // /disc list
            discNode.then(
                literal("list").executes { ctx ->
                    val source = ctx.source
                    val discs = PeyajCustomDiscFabric.discManager.getAllDiscs()
                    if (discs.isEmpty()) {
                        source.sendMessage(Text.literal("No custom discs registered yet. Use /disc web to create one!").formatted(Formatting.YELLOW))
                        return@executes 1
                    }

                    source.sendMessage(Text.literal("Registered Custom Discs (${discs.size}):").formatted(Formatting.GOLD))
                    discs.forEach { disc ->
                        val mins = disc.durationSeconds / 60
                        val secs = disc.durationSeconds % 60
                        val durStr = String.format("%d:%02d", mins, secs)
                        source.sendMessage(
                            Text.literal("• ").formatted(Formatting.GRAY)
                                .append(Text.literal(disc.id).formatted(Formatting.AQUA))
                                .append(Text.literal(" - \"${disc.name}\" by ${disc.author} [$durStr]").formatted(Formatting.YELLOW))
                        )
                    }
                    1
                }
            )

            // /disc play <discId>
            discNode.then(
                literal("play")
                    .then(
                        argument("discId", StringArgumentType.word())
                            .suggests { _, builder ->
                                PeyajCustomDiscFabric.discManager.getAllDiscs().forEach { disc ->
                                    builder.suggest(disc.id)
                                }
                                builder.buildFuture()
                            }
                            .executes { ctx ->
                                val source = ctx.source
                                val player = source.player ?: run {
                                    source.sendMessage(Text.literal("Players only.").formatted(Formatting.RED))
                                    return@executes 0
                                }
                                val discId = StringArgumentType.getString(ctx, "discId")
                                val disc = PeyajCustomDiscFabric.discManager.getDisc(discId) ?: run {
                                    source.sendMessage(Text.literal("Disc '$discId' not found.").formatted(Formatting.RED))
                                    return@executes 0
                                }
                                val soundId = Identifier.of("peyajcustomdisc", "disc.$discId")
                                val soundEvent = SoundEvent.of(soundId)
                                player.world.playSound(null, player.x, player.y, player.z, soundEvent, SoundCategory.RECORDS, 1.0f, 1.0f)
                                val msg = Text.literal("♫ Now Playing: ").formatted(Formatting.GOLD)
                                    .append(Text.literal(disc.name).formatted(Formatting.AQUA))
                                    .append(Text.literal(" by ").formatted(Formatting.GRAY))
                                    .append(Text.literal(disc.author).formatted(Formatting.YELLOW))
                                player.sendMessage(msg, true)
                                1
                            }
                    )
            )

            // /disc stop
            discNode.then(
                literal("stop").executes { ctx ->
                    val source = ctx.source
                    val player = source.player ?: run {
                        source.sendMessage(Text.literal("Players only.").formatted(Formatting.RED))
                        return@executes 0
                    }
                    val packet = StopSoundS2CPacket(null, SoundCategory.RECORDS)
                    player.networkHandler.sendPacket(packet)
                    player.sendMessage(Text.literal("Stopped Music").formatted(Formatting.RED), true)
                    1
                }
            )

            // /disc give <player> <discId>
            discNode.then(
                literal("give")
                    .then(
                        argument("target", EntityArgumentType.player())
                            .then(
                                argument("discId", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        PeyajCustomDiscFabric.discManager.getAllDiscs().forEach { disc ->
                                            builder.suggest(disc.id)
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        executeGive(ctx)
                                    }
                            )
                    )
            )

            dispatcher.register(discNode)
        }
    }

    private fun executeGive(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val target = EntityArgumentType.getPlayer(ctx, "target")
        val discId = StringArgumentType.getString(ctx, "discId")

        val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(discId)
        if (discItem == null) {
            source.sendMessage(Text.literal("Error: Disc with ID '$discId' does not exist!").formatted(Formatting.RED))
            return 0
        }

        val given = target.inventory.insertStack(discItem)
        if (!given) {
            target.dropItem(discItem, false)
        }

        val disc = PeyajCustomDiscFabric.discManager.getDisc(discId)!!
        source.sendMessage(
            Text.literal("Gave 1x \"${disc.name}\" to ").formatted(Formatting.GREEN)
                .append(target.displayName)
        )
        target.sendMessage(
            Text.literal("You received custom disc: \"${disc.name}\"!").formatted(Formatting.AQUA)
        )
        return 1
    }
}

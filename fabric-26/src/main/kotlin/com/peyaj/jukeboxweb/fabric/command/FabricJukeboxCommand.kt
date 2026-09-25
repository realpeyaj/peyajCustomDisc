package com.peyaj.jukeboxweb.fabric.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.peyaj.jukeboxweb.auth.AuthManager
import com.peyaj.jukeboxweb.fabric.PeyajCustomDiscFabric
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object FabricJukeboxCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val discNode = literal("disc")
                .requires { source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.byId(2))) }

            // /disc web
            discNode.then(
                literal("web").executes { ctx ->
                    val source = ctx.source
                    val player = source.player ?: run {
                        source.sendFailure(Component.literal("This command must be executed by a player.").withStyle(ChatFormatting.RED))
                        return@executes 0
                    }

                    val token = AuthManager.createLoginToken(player.uuid)
                    val urlBase = PeyajCustomDiscFabric.config.publicUrl.trimEnd('/')
                    val webUrl = "$urlBase/login?token=$token"

                    source.sendSystemMessage(Component.literal("Disc Creator Web Interface").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    source.sendSystemMessage(Component.literal("Click the link below to log in securely:").withStyle(ChatFormatting.GRAY))
                    
                    val link = Component.literal("▶ Click here to open Disc Creator")
                        .withStyle { style: Style ->
                            style.withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(ClickEvent.OpenUrl(java.net.URI.create(webUrl)))
                                .withHoverEvent(HoverEvent.ShowText(Component.literal("Open $webUrl in your web browser")))
                        }
                    source.sendSystemMessage(link)
                    source.sendSystemMessage(Component.literal("Note: This link is one-time use.").withStyle(ChatFormatting.DARK_GRAY))
                    1
                }
            )

            // /disc reload
            discNode.then(
                literal("reload").executes { ctx ->
                    val source = ctx.source
                    PeyajCustomDiscFabric.reload()
                    source.sendSystemMessage(Component.literal("✔ peyajCustomDisc configuration and WebServer reloaded!").withStyle(ChatFormatting.GREEN))
                    1
                }
            )

            // /disc list
            discNode.then(
                literal("list").executes { ctx ->
                    val source = ctx.source
                    val discs = PeyajCustomDiscFabric.discManager.getAllDiscs()
                    if (discs.isEmpty()) {
                        source.sendSystemMessage(Component.literal("No custom discs registered yet. Use /disc web to create one!").withStyle(ChatFormatting.YELLOW))
                        return@executes 1
                    }

                    source.sendSystemMessage(Component.literal("Registered Custom Discs (${discs.size}):").withStyle(ChatFormatting.GOLD))
                    discs.forEach { disc ->
                        val mins = disc.durationSeconds / 60
                        val secs = disc.durationSeconds % 60
                        val durStr = String.format("%d:%02d", mins, secs)
                        source.sendSystemMessage(
                            Component.literal("• ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(disc.id).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(" - \"${disc.name}\" by ${disc.author} [$durStr]").withStyle(ChatFormatting.YELLOW))
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
                                    source.sendFailure(Component.literal("Players only.").withStyle(ChatFormatting.RED))
                                    return@executes 0
                                }
                                val discId = StringArgumentType.getString(ctx, "discId")
                                val disc = PeyajCustomDiscFabric.discManager.getDisc(discId) ?: run {
                                    source.sendFailure(Component.literal("Disc '$discId' not found.").withStyle(ChatFormatting.RED))
                                    return@executes 0
                                }
                                val soundId = Identifier.fromNamespaceAndPath("peyajcustomdisc", "disc.$discId")
                                val soundEvent = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(soundId)
                                player.level().playSound(null, player.x, player.y, player.z, soundEvent, net.minecraft.sounds.SoundSource.RECORDS, 1.0f, 1.0f)
                                val msg = Component.literal("♫ Now Playing: ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(disc.name).withStyle(ChatFormatting.AQUA))
                                    .append(Component.literal(" by ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(disc.author).withStyle(ChatFormatting.YELLOW))
                                player.sendSystemMessage(msg, true)
                                1
                            }
                    )
            )

            // /disc stop
            discNode.then(
                literal("stop").executes { ctx ->
                    val source = ctx.source
                    val player = source.player ?: run {
                        source.sendFailure(Component.literal("Players only.").withStyle(ChatFormatting.RED))
                        return@executes 0
                    }
                    val packet = net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, net.minecraft.sounds.SoundSource.RECORDS)
                    player.connection.send(packet)
                    player.sendSystemMessage(Component.literal("Stopped Music").withStyle(ChatFormatting.RED), true)
                    1
                }
            )

            // /disc give <player> <discId>
            discNode.then(
                literal("give")
                    .then(
                        argument("target", EntityArgument.player())
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

    private fun executeGive(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val target = EntityArgument.getPlayer(ctx, "target")
        val discId = StringArgumentType.getString(ctx, "discId")

        val discItem = PeyajCustomDiscFabric.discManager.createDiscItem(discId)
        if (discItem == null) {
            source.sendFailure(Component.literal("Error: Disc with ID '$discId' does not exist!").withStyle(ChatFormatting.RED))
            return 0
        }

        val given = target.inventory.add(discItem)
        if (!given) {
            target.drop(discItem, false, net.minecraft.util.Prediction.SERVER_ONLY)
        }

        val disc = PeyajCustomDiscFabric.discManager.getDisc(discId)!!
        source.sendSystemMessage(
            Component.literal("Gave 1x \"${disc.name}\" to ").withStyle(ChatFormatting.GREEN)
                .append(target.displayName)
        )
        target.sendSystemMessage(
            Component.literal("You received custom disc: \"${disc.name}\"!").withStyle(ChatFormatting.AQUA)
        )
        return 1
    }
}

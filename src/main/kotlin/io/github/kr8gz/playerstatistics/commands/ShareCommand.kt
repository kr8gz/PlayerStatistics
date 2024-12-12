package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.space
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.Texts
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.broadcastText
import net.silkmc.silk.core.text.literalText
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ShareCommand : StatsCommand("share") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        executes { source.shareStoredData() }
        argument<UUID>("code") { code ->
            executes { source.shareStoredData(code()) }
        }
    }

    private data class ShareData(val label: Text, val content: Text, var shared: Boolean)

    private const val PLAYER_SAVE_LIMIT = 20

    private val playerShareStorage = object : ConcurrentHashMap<UUID, LinkedHashMap<UUID, ShareData>>() {
        fun getOrCreate(uuid: UUID) = getOrPut(uuid) {
            object : LinkedHashMap<UUID, ShareData>() {
                override fun removeEldestEntry(eldest: Map.Entry<UUID, ShareData>?) = size > PLAYER_SAVE_LIMIT
            }
        }
    }

    fun ServerCommandSource.storeShareData(label: Text, content: Text): UUID = UUID.randomUUID().also { code ->
        playerShareStorage.getOrCreate(uuid)[code] = ShareData(label, content, false)
    }

    private fun ServerCommandSource.shareStoredData(code: UUID? = null) {
        val playerStorage = playerShareStorage.getOrCreate(uuid)

        val actualCode = code ?: playerStorage.keys.lastOrNull() ?: throw CommandExceptions.NO_DATA.create()
        val data = playerStorage[actualCode] ?: throw CommandExceptions.SHARE_UNAVAILABLE.create()

        if (data.shared) throw CommandExceptions.ALREADY_SHARED.create()

        val message = run {
            val sharerName = (entity?.displayName ?: literalText(name)).build { color = config.colors.text.alt }
            Text.translatable("playerstatistics.command.share.message", sharerName, data.label).build { color = config.colors.text.main }
        }
        val hoverText = Texts.bracketed(Text.translatable("playerstatistics.command.share.hover")).build {
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, data.content)
            color = config.colors.action
        }
        server.broadcastText(message space hoverText)

        data.shared = true
    }
}

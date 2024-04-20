package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.Colors
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

    private val storedShareData = ConcurrentHashMap<UUID, LinkedHashMap<UUID, ShareData>>()

    private const val PLAYER_SAVE_LIMIT = 20

    private fun getShareData(uuid: UUID) = storedShareData.getOrPut(uuid) {
        object : LinkedHashMap<UUID, ShareData>() {
            override fun removeEldestEntry(eldest: Map.Entry<UUID, ShareData>?) = size > PLAYER_SAVE_LIMIT
        }
    }

    fun ServerCommandSource.storeShareData(label: Text, content: Text): UUID = UUID.randomUUID().also { code ->
        getShareData(uuid)[code] = ShareData(label, content, false)
    }

    private fun ServerCommandSource.shareStoredData(code: UUID? = null) {
        val shareData = getShareData(uuid)

        val actualCode = code ?: shareData.keys.lastOrNull()
            ?: throw Exceptions.NO_SHARE_RESULTS.create()

        val data = shareData[actualCode]
            ?: throw Exceptions.SHARE_UNAVAILABLE.create()

        if (data.shared) throw Exceptions.ALREADY_SHARED.create() else data.shared = true

        server.broadcastText {
            val sharerName = (entity?.displayName ?: literalText(name)).build { color = Colors.WHITE }
            val message = Text.translatable("playerstatistics.command.share.message", sharerName, data.label)

            val hoverText = Texts.bracketed(Text.translatable("playerstatistics.command.share.hover")).build {
                hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, data.content)
            }

            text(message.withColor(Colors.GRAY) space hoverText.withColor(Colors.GREEN))
        }
    }
}

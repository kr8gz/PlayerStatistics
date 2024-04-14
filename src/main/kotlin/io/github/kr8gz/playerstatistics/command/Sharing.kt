package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.extensions.MutableText.plus
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.Texts
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private data class ShareData(val label: Text, val content: Text, var shared: Boolean)

private val storedShareData = ConcurrentHashMap<UUID, LinkedHashMap<UUID, ShareData>>()

private const val PLAYER_SAVE_LIMIT = 20

private fun getShareDataFor(uuid: UUID) = storedShareData.getOrPut(uuid) {
    object : LinkedHashMap<UUID, ShareData>() {
        override fun removeEldestEntry(eldest: Map.Entry<UUID, ShareData>?) = size > PLAYER_SAVE_LIMIT
    }
}

fun ServerCommandSource.storeShareData(label: Text, content: Text): UUID = UUID.randomUUID().also { code ->
    getShareDataFor(uuid)[code] = ShareData(label, content, false)
}

fun ServerCommandSource.shareStoredData(code: UUID? = null) {
    val shareData = getShareDataFor(uuid)

    val actualCode = code
        ?: shareData.keys.lastOrNull()
        ?: throw StatsCommand.Exceptions.NO_SHARE_RESULTS.create()

    val data = shareData[actualCode]
        ?: throw StatsCommand.Exceptions.SHARE_UNAVAILABLE.create()

    if (data.shared) throw StatsCommand.Exceptions.ALREADY_SHARED.create() else data.shared = true

    val hoverText = Texts.bracketed(Text.translatable("playerstatistics.command.share.hover")).styled {
        it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, data.content))
    }
    val message = Text.translatable("playerstatistics.command.share.message", entity?.displayName ?: name, data.label)
    server.playerManager.broadcast(message + " " + hoverText, false)
}

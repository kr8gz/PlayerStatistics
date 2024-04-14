package io.github.kr8gz.playerstatistics.messages

import net.minecraft.text.*
import java.util.*

object Components {
    fun shareButton(code: UUID): Text {
        return Texts.bracketed(Text.translatable("playerstatistics.command.share")).styled {
            it  .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.share.hint")))
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats share $code"))
        }
    }

    fun pageFooter(page: Int, maxPage: Int, shareCode: UUID): Text = Text.empty().apply {
        fun dashes(count: Int) = Text.literal("-".repeat(count)).withColor(Colors.GRAY)

        fun pageButton(text: String, active: Boolean, newPage: Int, translationKey: String) = Text.literal(text).apply {
            return if (active) withColor(Colors.WHITE).styled {
                it  .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(translationKey)))
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats page $newPage"))
            } else withColor(Colors.GRAY)
        }

        append(dashes(7))
        append(pageButton(" ◀ [ ", active = page > 1, newPage = page - 1, "playerstatistics.command.page.previous"))
        append(Text.translatable("playerstatistics.command.page",
            Text.literal(page.coerceAtMost(maxPage).toString()),
            Text.literal(maxPage.toString()),
        ))
        append(pageButton(" ] ▶ ", active = page < maxPage, newPage = page + 1, "playerstatistics.command.page.next"))
        append(dashes(2))
        append(" ")
        append(shareButton(shareCode))
        append(" ")
        append(dashes(7))
    }
}

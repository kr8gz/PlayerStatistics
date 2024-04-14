package io.github.kr8gz.playerstatistics.messages

import net.minecraft.text.*
import net.silkmc.silk.core.text.literalText
import java.util.*

object Components {
    fun shareButton(code: UUID) = literalText {
        text(Texts.bracketed(Text.translatable("playerstatistics.command.share"))) {
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.share.hint"))
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats share $code")
        }
    }

    fun pageFooter(page: Int, maxPage: Int, shareCode: UUID): Text = literalText {
        fun dashes(count: Int) = text("-".repeat(count)) { color = Colors.GRAY }

        fun pageButton(text: String, active: Boolean, newPage: Int, translationKey: String) = text(text) {
            if (active) {
                color = Colors.WHITE
                hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(translationKey))
                clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats page $newPage")
            } else {
                color = Colors.GRAY
            }
        }

        dashes(7)
        pageButton(" ◀ [ ", active = page > 1, newPage = page - 1, "playerstatistics.command.page.previous")
        text(Text.translatable("playerstatistics.command.page",
            literalText(page.coerceAtMost(maxPage).toString()),
            literalText(maxPage.toString()),
        ))
        pageButton(" ] ▶ ", active = page < maxPage, newPage = page + 1, "playerstatistics.command.page.next")
        dashes(2)
        text(" ")
        text(shareButton(shareCode))
        text(" ")
        dashes(7)
    }
}

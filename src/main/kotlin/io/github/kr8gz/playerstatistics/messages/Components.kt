package io.github.kr8gz.playerstatistics.messages

import io.github.kr8gz.playerstatistics.commands.PageCommand
import io.github.kr8gz.playerstatistics.commands.ShareCommand
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.space
import net.minecraft.text.*
import net.silkmc.silk.core.text.literalText
import java.util.UUID

object Components {
    fun shareButton(code: UUID) = Texts.bracketed(Text.translatable("playerstatistics.command.share")).build {
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.share.hint"))
        clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, ShareCommand.formatCommand(code))
        color = config.colors.action
    }

    fun pageFooter(page: Int, maxPage: Int, shareCode: UUID): Text = literalText {
        fun dashes(count: Int) = literalText("-".repeat(count)) { color = config.colors.footer.main }

        fun pageButton(text: String, active: Boolean, newPage: Int, translationKey: String) = text(text) {
            color = config.colors.footer.altIf(active)
            if (active) {
                hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(translationKey))
                clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, PageCommand.formatCommand(newPage))
            }
        }

        text(dashes(7))
        pageButton(" ◀ [ ", active = page > 1, newPage = page - 1, "playerstatistics.command.page.previous")
        text(Text.translatable("playerstatistics.command.page",
            literalText(page.coerceAtMost(maxPage).toString()) { color = config.colors.pageNumber.alt },
            literalText(maxPage.toString()) { color = config.colors.pageNumber.main },
        ))
        pageButton(" ] ▶ ", active = page < maxPage, newPage = page + 1, "playerstatistics.command.page.next")
        text(dashes(2) space shareButton(shareCode) space dashes(7))
    }

    fun posDisplay(pos: Int, max: Int): Text = literalText {
        text("(")
        text(pos.toString()) { color = config.colors.pageNumber.alt }
        text("/")
        text(max.toString()) { color = config.colors.pageNumber.main }
        text(")")
        color = config.colors.text.main
    }
}

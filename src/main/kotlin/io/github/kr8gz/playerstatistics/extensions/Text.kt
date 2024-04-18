package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.text.Text
import net.silkmc.silk.core.text.literalText

object Text {
    infix fun Text.newLine(text: Text) = literalText {
        text(this@newLine)
        newLine()
        text(text)
    }

    infix fun Text.space(text: Text) = literalText {
        text(this@space)
        text(" ")
        text(text)
    }
}

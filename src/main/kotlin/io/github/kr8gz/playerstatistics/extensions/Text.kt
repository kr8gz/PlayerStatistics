package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.text.Text
import net.silkmc.silk.core.text.LiteralTextBuilder
import net.silkmc.silk.core.text.literalText

object Text {
    inline fun Text.build(builder: LiteralTextBuilder.() -> Unit) = literalText {
        text(this@build)
        builder()
    }

    infix fun Text.newLine(text: Text) = this.build {
        newLine()
        text(text)
    }

    infix fun Text.space(text: Text) = this.build {
        text(" ")
        text(text)
    }
}

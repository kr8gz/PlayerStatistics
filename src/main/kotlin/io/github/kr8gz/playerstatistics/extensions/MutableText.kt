package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.text.MutableText
import net.minecraft.text.Text

object MutableText {
    operator fun MutableText.plus(text: String): MutableText = append(text)
    operator fun MutableText.plus(text: Text): MutableText = append(text)
}

package io.github.kr8gz.playerstatistics.util

import io.github.kr8gz.playerstatistics.format.formatDecimalString
import net.minecraft.text.Text
import net.silkmc.silk.core.text.literalText

abstract class StatSource {
    open val databaseKey: String? = null

    abstract fun formatCommandArgs(): String

    abstract fun formatNameText(): Text

    fun formatValueText(value: Number) = formatValueText(value.toLong())

    protected open fun formatValueText(value: Long): Text = literalText(formatDecimalString(value))
}

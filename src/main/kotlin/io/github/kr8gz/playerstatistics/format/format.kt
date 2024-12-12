package io.github.kr8gz.playerstatistics.format

import io.github.kr8gz.playerstatistics.config.config
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.silkmc.silk.core.text.literalText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val decimalFormatter = DecimalFormat(",###.##", DecimalFormatSymbols(Locale.US))

fun formatDecimalString(value: Number): String = decimalFormatter.format(value)

fun formatUnitString(vararg units: Pair<Number, String>): String {
    val sortedUnits = units.map { (value, unit) -> value.toDouble() to unit }.sortedBy { it.first }
    val (value, unit) = sortedUnits.find { it.first > 0.5 } ?: sortedUnits.last()
    return "${formatDecimalString(value)} $unit"
}

fun formatDistanceText(value: Long): Text {
    val meters = value.toDouble() / 100
    val kilometers = meters / 1000

    return literalText(formatUnitString(kilometers to "km", meters to "m", value to "cm")) {
        val hoverText = Text.translatable("playerstatistics.unit.blocks", formatDecimalString(meters))
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
    }
}

fun formatTimeText(value: Long): Text {
    val seconds = value.toDouble() / 20
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val years = days / 365

    return literalText(formatUnitString(years to "y", days to "d", hours to "h", minutes to "m", seconds to "s")) {
        val hoverText = Text.translatable("playerstatistics.unit.ticks", formatDecimalString(value))
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
    }
}

fun formatHeartsText(value: Long): Text {
    return literalText(formatDecimalString(value / 20.0)) {
        text(" ❤") { color = config.colors.heart; bold = false }
        val hoverText = Text.translatable("playerstatistics.unit.damage", formatDecimalString(value / 10.0))
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
    }
}

package io.github.kr8gz.playerstatistics.messages

import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.StatType.identifier
import net.minecraft.block.Block
import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.stat.Stat
import net.minecraft.stat.StatFormatter
import net.minecraft.stat.Stats
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.silkmc.silk.core.text.literalText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val decimalFormatter = DecimalFormat(",###.##", DecimalFormatSymbols())
fun formatNumber(value: Number): String = decimalFormatter.format(value)

private fun formatWithUnit(vararg units: Pair<Number, String>): String {
    val sortedUnits = units.map { (value, unit) -> value.toDouble() to unit }.sortedBy { it.first }
    val (value, unit) = sortedUnits.find { it.first > 0.5 } ?: sortedUnits.last()
    return "${formatNumber(value)} $unit"
}

fun Stat<*>.formatName(): Text {
    fun formatWithStatType(statText: Text): Text {
        return Text.translatable("playerstatistics.stat_type.${type.identifier.toTranslationKey()}", statText)
    }

    fun formatItemText(item: Item) = formatWithStatType(literalText {
        text(item.name)
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(item.defaultStack))
    })

    return when (val obj = value) {
        is Block -> obj.asItem().takeIf { it != Items.AIR }?.let(::formatItemText) ?: formatWithStatType(obj.name)
        is Item -> formatItemText(obj)
        is EntityType<*> -> formatWithStatType(obj.name)
        is Identifier -> Text.translatable("stat.${obj.toTranslationKey()}")
        else -> literalText(name)
    }
}

fun Stat<*>.formatValue(value: Int) = formatValue(value.toLong())
fun Stat<*>.formatValue(value: Long): Text = when (formatter) {
    StatFormatter.DISTANCE -> {
        val meters = value / 100.0
        val kilometers = meters / 1000.0

        literalText(formatWithUnit(kilometers to "km", meters to "m", value to "cm")) {
            val hoverText = Text.translatable("playerstatistics.format.blocks", formatNumber(meters))
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
        }
    }
    StatFormatter.TIME -> {
        val seconds = value / 20.0
        val minutes = seconds / 60.0
        val hours = minutes / 60.0
        val days = hours / 24.0
        val years = days / 365.0

        literalText(formatWithUnit(years to "y", days to "d", hours to "h", minutes to "m", seconds to "s")) {
            val hoverText = Text.translatable("playerstatistics.format.ticks", formatNumber(value))
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
        }
    }
    StatFormatter.DIVIDE_BY_TEN -> {
        literalText(formatNumber(value / 20.0)) {
            text(" ❤") { color = Colors.HEART }
            val hoverText = Text.translatable("playerstatistics.format.damage", formatNumber(value / 10.0))
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
        }
    }
    else -> {
        literalText(formatNumber(value))
    }
}

fun <T> Stat<T>.asCommandArguments(): String {
    val identifier = type.registry.getKey(value).get().value
    return if (type == Stats.CUSTOM) identifier.toShortString() else "${type.identifier.path} $identifier"
}

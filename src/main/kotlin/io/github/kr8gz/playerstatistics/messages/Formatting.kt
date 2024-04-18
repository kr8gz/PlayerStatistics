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

private fun Number.withUnit(unit: String) = "${formatNumber(this)} $unit"

fun Stat<*>.formatValue(value: Int) = formatValue(value.toLong())
fun Stat<*>.formatValue(value: Long): Text = when (formatter) {
    StatFormatter.DISTANCE -> {
        val meters = value / 100.0
        val kilometers = meters / 1000.0

        literalText(when {
            kilometers > 0.5 -> kilometers.withUnit("km")
            meters > 0.5 -> meters.withUnit("m")
            else -> value.withUnit("cm")
        }) {
            val hoverText = Text.translatable("playerstatistics.format.blocks", formatNumber(meters))
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText) // FIXME why do these not appear
        }
    }
    StatFormatter.TIME -> {
        val seconds = value / 20.0
        val minutes = seconds / 60.0
        val hours = minutes / 60.0
        val days = hours / 24.0
        val years = days / 365.0

        literalText(when {
            years > 0.5 -> years.withUnit("y")
            days > 0.5 -> days.withUnit("d")
            hours > 0.5 -> hours.withUnit("h")
            minutes > 0.5 -> minutes.withUnit("m")
            else -> seconds.withUnit("s")
        }) {
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
    val statId = type.registry.getKey(value).get().value
    return if (type == Stats.CUSTOM) statId.toShortString() else "${type.identifier.path} $statId"
}

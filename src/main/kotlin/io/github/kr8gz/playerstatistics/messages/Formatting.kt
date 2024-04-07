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
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val decimalFormatter = DecimalFormat(",###.##", DecimalFormatSymbols())
fun formatDecimal(value: Any): String = decimalFormatter.format(value)

fun Stat<*>.formatName(): MutableText {
    fun formatWithStatType(statText: Text): MutableText {
        return Text.translatable("playerstatistics.stat_type.${type.identifier.toTranslationKey()}", statText)
    }

    fun formatItemText(item: Item) = formatWithStatType(Text.empty().append(item.name).styled {
        it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(item.defaultStack)))
    })

    return when (val obj = value) {
        is Block -> obj.asItem().takeIf { it != Items.AIR }?.let(::formatItemText) ?: formatWithStatType(obj.name)
        is Item -> formatItemText(obj)
        is EntityType<*> -> formatWithStatType(obj.name)
        is Identifier -> Text.translatable("stat.${obj.toTranslationKey()}")
        else -> Text.literal(name)
    }
}

fun Stat<*>.formatValue(value: Int): MutableText = when (formatter) {
    StatFormatter.DISTANCE -> {
        Text.literal(format(value)).styled {
            val hoverText = Text.translatable("playerstatistics.format.blocks", formatDecimal(value / 100.0))
            it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
        }
    }
    StatFormatter.DIVIDE_BY_TEN -> {
        Text.literal(formatDecimal(value / 20.0)).apply {
            append(Text.literal(" ❤").withColor(Colors.HEART))
            styled {
                val hoverText = Text.translatable("playerstatistics.format.damage", formatDecimal(value / 10.0))
                it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
            }
        }
    }
    StatFormatter.TIME -> {
        Text.literal(format(value)).styled {
            val hoverText = Text.translatable("playerstatistics.format.ticks", StatFormatter.DEFAULT.format(value))
            it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
        }
    }
    else -> {
        Text.literal(format(value))
    }
}

fun <T> Stat<T>.asCommandArguments(): String {
    val statId = type.registry.getKey(value).get().value
    return if (type == Stats.CUSTOM) statId.toShortString() else "${type.identifier.path} $statId"
}

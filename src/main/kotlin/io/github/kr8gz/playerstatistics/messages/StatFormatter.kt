package io.github.kr8gz.playerstatistics.messages

import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.StatType.identifier
import io.github.kr8gz.playerstatistics.extensions.Text.build
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

class StatFormatter<T>(private val stat: Stat<T>) {
    val name: Text by lazy {
        fun formatWithStatType(text: Text): Text {
            return Text.translatable("playerstatistics.stat_type.${stat.type.identifier.toTranslationKey()}", text)
        }

        fun formatItemText(item: Item) = formatWithStatType(item.name.build {
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(item.defaultStack))
        })

        when (val obj = stat.value) {
            is Block -> obj.asItem().takeIf { it != Items.AIR }?.let(::formatItemText) ?: formatWithStatType(obj.name)
            is Item -> formatItemText(obj)
            is EntityType<*> -> formatWithStatType(obj.name)
            is Identifier -> Text.translatable("stat.${obj.toTranslationKey()}")
            else -> literalText(stat.name)
        }
    }

    private val valueFormatter: (Long) -> Text by lazy {
        fun formatWithUnit(vararg units: Pair<Number, String>): String {
            val sortedUnits = units.map { (value, unit) -> value.toDouble() to unit }.sortedBy { it.first }
            val (value, unit) = sortedUnits.find { it.first > 0.5 } ?: sortedUnits.last()
            return "${formatNumber(value)} $unit"
        }

        when (stat.formatter) {
            StatFormatter.DISTANCE -> { value ->
                val meters = value.toDouble() / 100
                val kilometers = meters / 1000

                literalText(formatWithUnit(kilometers to "km", meters to "m", value to "cm")) {
                    val hoverText = Text.translatable("playerstatistics.format.blocks", formatNumber(meters))
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
                }
            }

            StatFormatter.TIME -> { value ->
                val seconds = value.toDouble() / 20
                val minutes = seconds / 60
                val hours = minutes / 60
                val days = hours / 24
                val years = days / 365

                literalText(formatWithUnit(years to "y", days to "d", hours to "h", minutes to "m", seconds to "s")) {
                    val hoverText = Text.translatable("playerstatistics.format.ticks", formatNumber(value))
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
                }
            }

            StatFormatter.DIVIDE_BY_TEN -> { value ->
                literalText(formatNumber(value / 20.0)) {
                    text(" ❤") { color = Colors.HEART; bold = false }
                    val hoverText = Text.translatable("playerstatistics.format.damage", formatNumber(value / 10.0))
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
                }
            }

            else -> { value -> literalText(formatNumber(value)) }
        }
    }

    fun formatValue(value: Number) = valueFormatter(value.toLong())

    val commandArguments by lazy {
        val identifier = stat.type.registry.getKey(stat.value).get().value
        when (stat.type) {
            Stats.CUSTOM -> identifier.toShortString()
            else -> "${stat.type.identifier.toShortString()} $identifier"
        }
    }
}

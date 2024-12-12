package io.github.kr8gz.playerstatistics.util

import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.StatType.identifier
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.format.*
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

class MinecraftStatSource(val stat: Stat<*>) : StatSource() {
    override val databaseKey: String = stat.name

    override fun formatCommandArgs(): String {
        fun <T> Stat<T>.getId() = type.registry.getKey(value).get().value
        return when (stat.type) {
            Stats.CUSTOM -> stat.getId().toShortString()
            else -> "${stat.type.identifier.toShortString()} ${stat.getId()}"
        }
    }

    private fun formatTextWithStatType(text: Text): Text {
        val statTypeKey = stat.type.identifier.toTranslationKey("playerstatistics.stat_type")
        return Text.translatable(statTypeKey, text)
    }

    private fun formatItemText(item: Item) = formatTextWithStatType(item.name.build {
        if (item != Items.AIR) {
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(item.defaultStack))
        }
    })

    override fun formatNameText(): Text = when (val obj = stat.value) {
        is Item -> formatItemText(obj)
        is Block -> formatItemText(obj.asItem())
        is EntityType<*> -> formatTextWithStatType(obj.name)
        is Identifier -> Text.translatable(obj.toTranslationKey("stat"))
        else -> literalText(stat.name)
    }

    override fun formatValueText(value: Long) = when (stat.formatter) {
        StatFormatter.DISTANCE -> formatDistanceText(value)
        StatFormatter.TIME -> formatTimeText(value)
        StatFormatter.DIVIDE_BY_TEN -> formatHeartsText(value)
        else -> super.formatValueText(value)
    }
}

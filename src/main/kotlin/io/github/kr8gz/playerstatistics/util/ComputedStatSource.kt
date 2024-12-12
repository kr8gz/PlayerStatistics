package io.github.kr8gz.playerstatistics.util

import io.github.kr8gz.playerstatistics.PlayerStatistics
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class ComputedStatSource private constructor(private val key: String) : StatSource() {
    private val id = Identifier.of(PlayerStatistics.MOD_ID, key)!!

    companion object {
        val TOP_STATS = ComputedStatSource("top_stats")
    }

    override fun formatNameText(): Text = Text.translatable(id.toTranslationKey("stat"))

    override fun formatCommandArgs() = key
}

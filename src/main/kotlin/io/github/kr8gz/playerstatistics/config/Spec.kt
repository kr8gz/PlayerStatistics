package io.github.kr8gz.playerstatistics.config

data class Config(
    val colors: ColorConfig,
)

private typealias Color = Int
data class ColorPair(val main: Color, val alt: Color = main) {
    fun altIf(condition: Boolean) = if (condition) alt else main
}

data class ColorConfig(
    val text: ColorPair,
    val extra: ColorPair,

    val rank: ColorPair,
    val name: ColorPair,

    val value: ColorPair,
    val heart: Color,

    val footer: ColorPair,
    val pageNumber: ColorPair,

    val action: Color,
    val noData: Color,
)

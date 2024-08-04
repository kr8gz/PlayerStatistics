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
    val rank: ColorPair,
    val name: ColorPair,
    val value: ColorPair,
    val heart: Color,
    val action: Color,
    val listOutput: ListOutputConfig,
) {
    data class ListOutputConfig(
        val extra: ColorPair,
        val footer: ColorPair,
        val pageNumbers: ColorPair,
        val noData: Color,
    )
}

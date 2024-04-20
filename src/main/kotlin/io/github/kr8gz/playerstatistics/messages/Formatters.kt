package io.github.kr8gz.playerstatistics.messages

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val decimalFormatter = DecimalFormat(",###.##", DecimalFormatSymbols())
fun formatNumber(value: Number): String = decimalFormatter.format(value)

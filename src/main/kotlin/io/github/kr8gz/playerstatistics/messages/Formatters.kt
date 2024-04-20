package io.github.kr8gz.playerstatistics.messages

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val decimalFormatter = DecimalFormat(",###.##", DecimalFormatSymbols(Locale.US))
fun formatNumber(value: Number): String = decimalFormatter.format(value)

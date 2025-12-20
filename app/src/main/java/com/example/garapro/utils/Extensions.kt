package com.example.garapro.utils

import java.text.DecimalFormat

fun Double.formatDistance(): String = "%.1f".format(this)

fun Double.formatPrice(): String {
    return try {
        val formatter = DecimalFormat("#,###")
        "${formatter.format(this)} đ"
    } catch (e: Exception) {
        "%,.0f đ".format(this)
    }
}

package com.example.garapro.utils

import com.example.garapro.data.model.repairRequest.Service
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyUtils {
    fun formatVietnameseCurrency(amount: Double): String {
        return try {
            val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
                groupingSeparator = '.'  // Dấu phân cách hàng nghìn
                decimalSeparator = ','   // Dấu phân cách thập phân (nếu cần)
            }

            val formatter = DecimalFormat("#,###", symbols)
            "${formatter.format(amount)} đ"
        } catch (e: Exception) {
            "$amount đ"
        }
    }

    fun calculateServicePrice(service: Service): Double {
        return if (service.discountedPrice > 0 && service.discountedPrice < service.price) {
            service.discountedPrice
        } else {
            service.price
        }
    }
}
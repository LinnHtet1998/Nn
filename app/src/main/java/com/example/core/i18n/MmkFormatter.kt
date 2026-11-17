package com.example.core.i18n

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MmkFormatter {
    private val enNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US)

    fun formatMmk(amount: Long, language: AppLanguage): String {
        val formatted = enNumberFormat.format(amount)
        return when (language) {
            AppLanguage.MYANMAR -> "$formatted ကျပ်"
            AppLanguage.ENGLISH -> "$formatted MMK"
        }
    }

    fun formatCount(count: Long): String = enNumberFormat.format(count)

    fun formatDateTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        return sdf.format(Date(epochMillis))
    }
}

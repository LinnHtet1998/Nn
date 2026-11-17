package com.example.core.i18n

enum class AppLanguage(val code: String, val nativeLabel: String) {
    MYANMAR("my", "မြန်မာ"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: MYANMAR
    }
}

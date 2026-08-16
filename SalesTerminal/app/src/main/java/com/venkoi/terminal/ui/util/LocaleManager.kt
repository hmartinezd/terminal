package com.venkoi.terminal.ui.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {
    fun setLocale(languageCode: String?) {
        val appLocale: LocaleListCompat = if (languageCode == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getCurrentLanguageCode(): String? {
        return AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { null }
    }
}

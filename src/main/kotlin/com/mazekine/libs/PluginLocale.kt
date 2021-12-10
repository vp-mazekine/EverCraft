package com.mazekine.libs

import org.slf4j.LoggerFactory
import java.text.MessageFormat
import java.util.*
import java.util.Locale

object PluginLocale {
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }

    var locale: ResourceBundle = ResourceBundle.getBundle("messages", Locale.ENGLISH)
        get() = field
        private set

    //  Symbols
    var currencyName:   String? = locale.getString("currency.name")
        get() = field
        private set
    var currencyLetter: String? = locale.getString("currency.letter")
        get() = field
        private set
    var currencyEmoji:  String? = locale.getString("currency.emoji")
        get() = field
        private set

    //  Prefixes
    var prefixError:    String? = locale.getString("prefix.error")
        get() = field
        private set
    var prefixRegular:  String? = locale.getString("prefix.regular")
        get() = field
        private set

    fun setLocale(newLocale: String) {
        try {
            locale = ResourceBundle.getBundle("messages", Locale(newLocale))

            currencyName   = locale.getString("currency.name")   ?: currencyName
            currencyLetter = locale.getString("currency.letter") ?: currencyLetter
            currencyEmoji  = locale.getString("currency.emoji")  ?: currencyEmoji
            prefixError    = locale.getString("prefix.error")    ?: prefixError
            prefixRegular  = locale.getString("prefix.regular")  ?: prefixRegular
        } catch (e: Exception) {
            logger.error("Wrong locale set in plugin configuration")
        }
    }

    fun getLocalizedError(code: String, args: Array<Any>? = null): String {
        val rawMessage = locale.getString(code) ?: return code

        return MessageFormat.format(
            "&c " +
            rawMessage,
            args
        )
    }

    fun getLocalizedMessage(code: String, args: Array<Any>? = null): String {
        val rawMessage = locale.getString(code) ?: return code

        return MessageFormat.format(
            "&r " +
            rawMessage,
            args
        )
    }
}
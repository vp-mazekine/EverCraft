package com.mazekine.libs

import net.kyori.adventure.text.Component
import net.md_5.bungee.api.ChatColor
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
    var currencyName: String? = locale.getString("currency.name")
        get() {
            return field?.let {
                ChatColor.translateAlternateColorCodes('&', it)
            }
        }
        private set
    var currencyLetter: String? = locale.getString("currency.letter")
        get() {
            return field?.let {
                ChatColor.translateAlternateColorCodes('&', it)
            }
        }
        private set
    var currencyEmoji: String? = locale.getString("currency.emoji")
        get() {
            return field?.let {
                ChatColor.translateAlternateColorCodes('&', it)
            }
        }
        private set

    //  Prefixes
    var prefixError: String? = locale.getString("prefix.error")
        get() {
            return field?.let {
                ChatColor.translateAlternateColorCodes('&', it)
            }
        }
        private set
    var prefixRegular: String? = locale.getString("prefix.regular")
        get() {
            return field?.let {
                ChatColor.translateAlternateColorCodes('&', it)
            }
        }
        private set

    fun setLocale(newLocale: String) {
        try {
            locale = ResourceBundle.getBundle("messages", Locale(newLocale))

            currencyName = locale.getString("currency.name") ?: currencyName
            currencyLetter = locale.getString("currency.letter") ?: currencyLetter
            currencyEmoji = locale.getString("currency.emoji") ?: currencyEmoji
            prefixError = locale.getString("prefix.error") ?: prefixError
            prefixRegular = locale.getString("prefix.regular") ?: prefixRegular
        } catch (e: Exception) {
            logger.error("Wrong locale set in plugin configuration")
        }
    }

    fun getLocalizedError(code: String, args: Array<out Any>? = null, colored: Boolean = true): String {
        var rawMessage = try {
            locale.getString(code)
        } catch (e: Exception) {
            return code
        }

        rawMessage = (if (colored) " &c" else "") + rawMessage
        rawMessage = ChatColor.translateAlternateColorCodes('&', rawMessage)

        return MessageFormat(rawMessage)
            .format(args)
    }

    fun getLocalizedError(code: String, args: Array<out Any>? = null, colored: Boolean = true, prefix: Boolean = true): Component {
        return Component.text(
            if(prefix) prefixError ?: "" else ""
        ).append(
            Component.text(
                getLocalizedError(code, args, colored)
            )
        )
    }

    fun getLocalizedMessage(code: String, args: Array<out Any>? = null, colored: Boolean = true): String {
        var rawMessage = try {
            locale.getString(code)
        } catch (e: Exception) {
            return code
        }

        rawMessage = (if (colored) " &r" else "") + rawMessage

        return ChatColor.translateAlternateColorCodes(
            '&',
            MessageFormat(rawMessage)
                .format(args)
        )
    }

    fun getLocalizedMessage(code: String, args: Array<out Any>? = null, colored: Boolean = true, prefix: Boolean = true): Component {
        return Component.text(
            if(prefix) prefixRegular ?: "" else ""
        ).append(
            Component.text(
                getLocalizedMessage(code, args, colored)
            )
        )
    }
}
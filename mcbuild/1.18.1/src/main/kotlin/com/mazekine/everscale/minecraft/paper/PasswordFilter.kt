package com.mazekine.everscale.minecraft.paper

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.message.Message

class PasswordFilter : AbstractFilter() {
    fun registerFilter() {
        val logger: Logger = LogManager.getRootLogger() as Logger
        logger.addFilter(this)
    }

    override fun filter(event: LogEvent?): Filter.Result {
        return if (event == null) Filter.Result.NEUTRAL else isLoggable(event.message.formattedMessage)
    }

    override fun filter(logger: Logger?, level: Level?, marker: Marker?, msg: Any?, t: Throwable?): Filter.Result {
        return isLoggable(msg.toString());
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        vararg params: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(logger: Logger?, level: Level?, marker: Marker?, msg: String?, p0: Any?): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?,
        p8: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(
        logger: Logger?,
        level: Level?,
        marker: Marker?,
        msg: String?,
        p0: Any?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?,
        p8: Any?,
        p9: Any?
    ): Filter.Result {
        return isLoggable(msg)
    }

    override fun filter(logger: Logger?, level: Level?, marker: Marker?, msg: Message?, t: Throwable?): Filter.Result {
        return isLoggable(msg?.formattedMessage)
    }

    private fun isLoggable(msg: String?): Filter.Result {
        if (msg != null) {
            if (msg.contains("issued server command:")) {
                if (
                    msg.containsAny(listOf(
                        "/e_register",
                        "/e_new_password",
                        "/e_pk",
                        "/e_signup",
                        "/e_new_pk",
                        "/e_send",
                        "/e_pay",
                        "/e_tip",
                        "/e_withdraw"
                    ))
                ) {
                    return Filter.Result.DENY
                }
            }
        }
        return Filter.Result.NEUTRAL
    }

    private fun String.containsAny(options: List<String>): Boolean {
        options.forEach {
            if(this.contains(it)) return true
        }
        return true
    }
}
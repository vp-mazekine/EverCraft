package com.mazekine.libs.bStats

import com.mazekine.everscale.minecraft.paper.Store
import com.mazekine.libs.PluginSecureStorage
import java.math.BigDecimal
import java.math.RoundingMode

object CustomMetrics {
    /**
     * Provision of coupon price to bStats
     *
     * @return [com.mazekine.libs.bStats.Metrics.CustomChart]
     */
    fun couponPrice(): Metrics.CustomChart {
        val range = when {
            Store.couponPrice < BigDecimal(0) -> "Negative"
            Store.couponPrice == BigDecimal(0) -> "Free"
            Store.couponPrice <= BigDecimal(0.1) -> "(0, 0.1]"
            Store.couponPrice <= BigDecimal(1) -> "(0.1, 1]"
            Store.couponPrice <= BigDecimal(10) -> "(1, 10]"
            Store.couponPrice <= BigDecimal(100) -> "(10, 100]"
            else -> "Over 100"
        }

        return Metrics.DrilldownPie("coupon_price_ever") {
            mapOf(
                range to mapOf(
                    Store.couponPrice.toPlainString() to 1
                )
            )
        }
    }

    /**
     * Returns stats on items in the store
     *
     * @return
     */
    fun storeItems(): Metrics.CustomChart {
        return Metrics.AdvancedPie("store_items") {
            val result: MutableMap<String, Int> = mutableMapOf()

            result.putAll(
                Store.storeItems.keys.map { it.name to 1 }
            )

            return@AdvancedPie result
        }
    }

    /**
     * Returns stats on average lot size, rounded to the closest integer
     *
     * @return
     */
    fun storeAverageLotSize(): Metrics.CustomChart {
        return Metrics.SimplePie("store_avg_lot_size") {
            val sum = Store.storeItems.values.fold(0) {sum, v -> sum + v.lotSize}
            val result = BigDecimal(sum)
                .divide(Store.storeItems.values.size.toBigDecimal(), RoundingMode.HALF_EVEN)
                .apply { this.setScale(0) }

            return@SimplePie result.toPlainString()
        }
    }

    /**
     * Returns stats on average lot price, rounded to the closest integer
     *
     * @return
     */
    fun storeAverageLotPrice(): Metrics.CustomChart {
        return Metrics.SimplePie("store_avg_lot_price") {
            val sum = Store.storeItems.values.fold(0) {sum, v -> sum + v.price}
            val result = BigDecimal(sum)
                .divide(Store.storeItems.values.size.toBigDecimal(), RoundingMode.HALF_EVEN)
                .apply { this.setScale(0) }

            return@SimplePie result.toPlainString()
        }
    }

    /**
     * Returns stats of plugin accounts number
     *
     * @return
     */
    fun everCraftAccounts(): Metrics.CustomChart {
        return Metrics.SingleLineChart("plugin_accounts") {
            return@SingleLineChart PluginSecureStorage
                .getAllPlayersData()
                .size
        }
    }

    /**
     * Returns stats of wallets number
     *
     * @return
     */
    fun everAccounts(): Metrics.CustomChart {
        return Metrics.SingleLineChart("ever_accounts") {
            return@SingleLineChart PluginSecureStorage
                .getAllPlayersData()
                .values
                .sumOf { it.addresses.size }
        }
    }
}
package com.mazekine.libs.migration.models.m_0_2_3

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Store configuration type
 *
 * @property couponPrice Floating price of one coupon in EVERs
 * @property items  Collection of store items
 * @constructor Create empty Config
 */
data class StoreConfig(
    @SerializedName("store_wallet")
    val storeWallet: String,
    @SerializedName("coupon_price")
    val couponPrice: BigDecimal,
    val items: List<StoreItem>,
    val pattern: Pattern? = null
)

data class Pattern(
    var path: String,
    val provider: SupportedUOP
)

enum class SupportedUOP {
    FixedBinary
}

/**
 * Store item
 *
 * @property type   Material name
 * @property price  Price in number of coupons per lot
 * @property lotSize   Number of items sold
 * @constructor Create empty Item
 */
data class StoreItem(
    val type: String,
    val price: Int,
    @SerializedName("lot_size")
    val lotSize: Int,
)

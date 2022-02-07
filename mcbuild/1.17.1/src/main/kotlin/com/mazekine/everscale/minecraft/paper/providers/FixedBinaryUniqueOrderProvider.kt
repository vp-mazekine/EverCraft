package com.mazekine.everscale.minecraft.paper.providers

import java.io.File

/**
 * This provider accepts a binary map where "1" indicates a valid place, and "0" - an ignored placeholder.
 *
 * All other symbols from the input string will be cut out and not used for building the provider.
 *
 * @constructor
 *
 * @param source    Source file with order map
 */
class FixedBinaryUniqueOrderProvider(source: File) : IUniqueOrderProvider {
    private val order: MutableList<Int> = mutableListOf()
    private var cursor: Int = -1
    override val places: Int
    override val rows: Int

    init {
        require(source.exists()) { "Source file is not available" }

        order.addAll(
            source
                .readText()
                .toCharArray()
                .filter { it == '0' || it == '1' }
                .map { it.digitToInt() }
                .toMutableList()
        )

        require(order.size.mod(ROW_LENGTH) == 0) { "Pattern length must be divisible by $ROW_LENGTH" }

        places = order.count { it == 1 }
        rows = order.size.div(ROW_LENGTH)
    }

    override fun next(): Int? {
        if (order.isEmpty()) return null

        while (cursor < order.lastIndex) {
            cursor++
            if (order[cursor] == 1) return cursor
        }

        return null
    }

    override fun nextFor(index: Int?): Int? {
        return next()
    }

    override fun restart() {
        cursor = -1
    }

    override fun placesInRows(n: Int): Int {
        var result = 0

        order.forEachIndexed { index, flag ->
            if(index > (n * ROW_LENGTH - 1)) return result
            if(flag == 1) result++
        }

        return result
    }
}
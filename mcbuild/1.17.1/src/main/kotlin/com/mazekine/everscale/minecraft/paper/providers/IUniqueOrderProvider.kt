package com.mazekine.everscale.minecraft.paper.providers

interface IUniqueOrderProvider {
    /**
     * Number of places available for distribution
     */
    val places: Int

    /**
     * The length of a row (for row calculation)
     */
    val ROW_LENGTH: Int
        get() = 9

    /**
     * Number of rows in the pattern
     */
    val rows: Int

    /**
     * Counts how many items can be places in first N rows
     *
     * @param n Number of rows to look in
     * @return
     */
    fun placesInRows(n: Int): Int

    /**
     * Returns the next ordered place in the inventory
     *
     * @return Null if no more places are available
     */
    fun next(): Int?

    /**
     * Finds the place for a specific item.
     * If no items is specified, acts as the `next()` function
     *
     * @param item
     * @return  Null if no more places are available
     */
    fun nextFor(index: Int? = null): Int?

    /**
     * Reset the cursor of places distribution
     */
    fun restart()
}
package com.mazekine.libs.migration

/**
 * Defines the basic structure of the migration framework for plugin updates
 */
interface IMigration {
    /**
     * Perform the migration
     *
     * @return  *true* if migration was successful, *else* otherwise
     * @see     rollback
     */
    fun migrate(): Boolean

    /**
     * Roll the migration back
     *
     * @return  *true* if rollback was successful, *else* otherwise
     * @see     migrate
     */
    fun rollback(): Boolean

    /**
     * Perform the backup before the migration
     *
     * @return  *true* if backup was successful, *else* otherwise
     * @see     migrate
     */
    fun backup(): Boolean

    /**
     * Checks if current migration has been performed already
     *
     * @return  *true* if the migration was done, *else* otherwise
     */
    fun isMigrated(): Boolean
}
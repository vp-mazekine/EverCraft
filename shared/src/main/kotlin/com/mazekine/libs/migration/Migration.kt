package com.mazekine.libs.migration

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.jvm.Throws

abstract class Migration(
    val toVersion: Triple<Int, Int, Int>
) : IMigration {

    protected val logger by lazy { LoggerFactory.getLogger(this::class.java) }
    protected val gson by lazy { GsonBuilder().setLenient().setPrettyPrinting().create() }
    protected val BACKUP_FILE_NAME =
        "migration_${toVersion.first}.${toVersion.second}.${toVersion.third}_${System.currentTimeMillis()}.zip"

    companion object {
        val migrations: MutableList<Migration> = mutableListOf()
        val logger by lazy { LoggerFactory.getLogger(this::class.java) }

        fun migrateAll() {
            logger.info("Performing migrations...")

            migrations.sortWith (
                compareBy<Migration> { it.toVersion.first }
                    .thenBy { it.toVersion.second }
                    .thenBy { it.toVersion.third }
            )

            migrations.forEach {
                if(!it.migrate()) {
                    throw RuntimeException("Migration tree failed at migrating to version " + it.toVersion.toList().joinToString("."))
                }
            }

            logger.info("Migrations successfully performed...")
        }
    }

    /**
     * Compare if this migration target version is older than the compared one
     *
     * @param   other   Migration to compare
     * @return  *true* if this migration is older than the compared one, *false* otherwise
     */
    fun olderThan(other: Migration): Boolean {
        if (this.toVersion.first < other.toVersion.first) return true
        if (this.toVersion.first > other.toVersion.first) return false

        if (this.toVersion.second < other.toVersion.second) return true
        if (this.toVersion.second > other.toVersion.second) return false

        if (this.toVersion.third < other.toVersion.third) return true

        return false
    }

    /**
     * Check if this migration targets newer version of the plugin than the other
     *
     * @param   other   Migration to compare
     * @return  *true* if this migration is newer than the compared one, *false* otherwise
     */
    fun newerThan(other: Migration): Boolean = (!sameTargetAs(other) && !olderThan(other))

    /**
     * Check if both migrations target the same version
     *
     * @param   other   Migration to compare
     * @return  *true* if migrations target the same plugin version, *false* otherwise
     */
    fun sameTargetAs(other: Migration): Boolean = (this.toVersion == other.toVersion)

    /**
     * Adds a directory entry into a zip file
     *
     * @param name          Name of the entry
     * @param dirToZip      Real directory to zip, if any
     * @param withChildren  Add children nodes, if any
     * @param filter        Pattern to filter children nodes
     */
    @Throws(IOException::class)
    protected fun ZipOutputStream.zipDirectory(
        name: String,
        dirToZip: File? = null,
        withChildren: Boolean = false,
        filter: Regex? = null
    ) {
        if(name == "" || name == "/") return
        val entryName = name + (if (name.endsWith("/")) "" else "/")
        logger.info("Zipping $entryName")
        val zipEntry = ZipEntry(entryName)

        //  Create directory in the ZIP file
        this.putNextEntry(zipEntry)
        this.closeEntry()

        //  Add children, if applicable
        dirToZip?.let { dir ->
            if (!withChildren) return
            if (!dir.exists()) return

            //  Get all children or apply filter, if any
            val children = filter?.let { f ->
                dirToZip.listFiles { _, name -> name.matches(f) }
            } ?: dirToZip.listFiles()

            children.forEach { node ->
                if (node.isDirectory) {
                    this.zipDirectory(
                        entryName + node.name,
                        node,
                        withChildren,
                        filter
                    )
                } else {
                    this.zipFile(
                        entryName + node.name,
                        node
                    )
                }
            }
        }
    }

    /**
     * Add a file to zip archive
     *
     * @param name      File name to use
     * @param fileToZip File to zip
     */
    @Throws(IOException::class)
    protected fun ZipOutputStream.zipFile(
        name: String,
        fileToZip: File
    ) {
        if (fileToZip.isHidden) return
        if (fileToZip.isDirectory || fileToZip.name.endsWith("/")) return
        if (!fileToZip.exists()) return

        val data = ByteArray(2048)

        FileInputStream(fileToZip).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val zipEntry = ZipEntry(name)
                zipEntry.time = fileToZip.lastModified()
                zipEntry.size = fileToZip.length()
                this.putNextEntry(zipEntry)

                while (true) {
                    val readBytes = bis.read(data)
                    if (readBytes == -1) break
                    this.write(data, 0, readBytes)
                }

                bis.close()
            }
            fis.close()
        }
    }
}
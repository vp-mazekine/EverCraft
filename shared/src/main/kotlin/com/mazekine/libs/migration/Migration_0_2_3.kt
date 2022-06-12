package com.mazekine.libs.migration

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.mazekine.libs.ChaCha20Poly1305
import com.mazekine.libs.PluginSecureStorage
import com.mazekine.libs.hexToByteArray
import com.mazekine.libs.migration.models.m_0_2_3.StoreConfig
import com.mazekine.libs.models.UserMappings
import com.mazekine.libs.models.UserMappings_0_2_2
import com.mazekine.libs.models.UserNotificationsStatus
import com.mazekine.libs.toHex
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipOutputStream
import kotlin.jvm.Throws

class Migration_0_2_3 : Migration(Triple(0, 2, 3)) {
    private val PLUGIN_DATA_FOLDER = "plugins/EverCraft"
    private val STORAGE_FOLDER = "$PLUGIN_DATA_FOLDER/storage"
    private val STORAGE_OLD_FOLDER = "evercraft"
    private val STORAGE_PRIVATE_KEY_FILE = "pk.dat"
    private val STORAGE_USER_DATA_FILE = "ud.dat"
    private val PATTERNS_FOLDER = "$PLUGIN_DATA_FOLDER/patterns"
    private val PATTERNS_TEMPLATE = "^.*\\.pat$".toRegex()
    private val CONFIG_STORE_FILE = "store.json"
    private val BACKUP_FOLDER = "$PLUGIN_DATA_FOLDER/backup"

    init {
        migrations += this
    }

    override fun migrate(): Boolean {
        if (isMigrated()) return true

        if (!backup()) {
            logger.error("Migration cannot proceed without backing up. Terminating...")
            return false
        }

        //  Creating folders
        Files.createDirectories(Paths.get(STORAGE_FOLDER))
        Files.createDirectories(Paths.get(PATTERNS_FOLDER))

        //  Moving files to a new location
        File("$STORAGE_OLD_FOLDER/$STORAGE_PRIVATE_KEY_FILE").let {
            if (it.exists()) {
                it.copyTo(
                    File("$STORAGE_FOLDER/$STORAGE_PRIVATE_KEY_FILE"), true
                )
                it.delete()
            }
        }

        File("$STORAGE_OLD_FOLDER/$STORAGE_USER_DATA_FILE").let {
            if (it.exists()) {
                it.copyTo(
                    File("$STORAGE_FOLDER/$STORAGE_USER_DATA_FILE"), true
                )
                it.delete()
            }
        }

        //  Upgrade the storage to a new version
        upgradeDataStorage(File("$STORAGE_FOLDER/$STORAGE_USER_DATA_FILE"), File("$PLUGIN_DATA_FOLDER/config.yml"))

        //  Delete old storage folder
        File(STORAGE_OLD_FOLDER).let {
            if (it.exists()) it.delete()
        }

        File("$PLUGIN_DATA_FOLDER/$CONFIG_STORE_FILE").let {
            if (it.exists()) {
                val storeConfig = gson.fromJson(it.bufferedReader(), StoreConfig::class.java)
                storeConfig.pattern?.let { pattern ->
                    val patternFile = File(pattern.path)
                    if (
                        patternFile.exists() &&
                        !patternFile.isDirectory &&
                        !patternFile.name.endsWith("/")
                    ) {
                        storeConfig.pattern.path = "$PATTERNS_FOLDER/${patternFile.name}"
                        it.setWritable(true)
                        it.writeText(gson.toJson(storeConfig))
                    }
                }
            }
        }

        File(PLUGIN_DATA_FOLDER).let {
            if (it.isDirectory) {
                it.listFiles { _, name -> name.matches(PATTERNS_TEMPLATE) }
                    ?.forEach { file ->
                        if (file.exists() && !file.isDirectory && !file.name.endsWith("/")) {
                            file.copyTo(
                                File("$PATTERNS_FOLDER/${file.name}")
                            )
                            file.delete()
                        }
                    }
            }
        }

        return if (!isMigrated()) {
            rollback()
            false
        } else {
            true
        }
    }

    override fun rollback(): Boolean {
        return true
    }

    override fun backup(): Boolean {
        Files.createDirectories(Paths.get(BACKUP_FOLDER))
        val backupFile = File("$BACKUP_FOLDER/$BACKUP_FILE_NAME")
        if (backupFile.exists()) backupFile.delete()
        backupFile.createNewFile()

        val fos = backupFile.outputStream()
        val zipOut = ZipOutputStream(fos)

        try {
            //  Perform backup
            if (File(STORAGE_OLD_FOLDER).exists() && File(STORAGE_OLD_FOLDER).isDirectory) {
                zipOut.zipDirectory(STORAGE_OLD_FOLDER, File(STORAGE_OLD_FOLDER), true)
            }
            zipOut.zipDirectory(PLUGIN_DATA_FOLDER, File(PLUGIN_DATA_FOLDER), true, "^((?!backup).)*$".toRegex())
        } catch (e: Exception) {
            zipOut.close()
            fos.close()

            logger.error(
                "Backup failed. " +
                        e.message + "\n" +
                        e.stackTraceToString()
            )
            backupFile.delete()
            return false
        }

        zipOut.close()
        fos.close()
        return true
    }

    /**
     * Checks if all the migrations were performed successfully
     *
     * @return  Boolean
     */
    override fun isMigrated(): Boolean {
        //  In case of a fresh setup
        if(
            !File("$STORAGE_OLD_FOLDER").exists()
        ) return true

        val dataFilesRelocated =
            File("$STORAGE_FOLDER/$STORAGE_PRIVATE_KEY_FILE").exists() &&
                    File("$STORAGE_FOLDER/$STORAGE_USER_DATA_FILE").exists()

        logger.debug(
            "[0.2.3 migration] " +
                    if (!dataFilesRelocated) {
                        "Storage files not relocated"
                    } else {
                        "Storage files relocated"
                    }
        )

        val oldDataFilesRemoved =
            !File("$STORAGE_OLD_FOLDER/$STORAGE_PRIVATE_KEY_FILE").exists() &&
                    !File("$STORAGE_OLD_FOLDER/$STORAGE_USER_DATA_FILE").exists() &&
                    !File(STORAGE_OLD_FOLDER).exists()

        logger.debug(
            "[0.2.3 migration] " +
                    if (!oldDataFilesRemoved) {
                        "Old storage files not removed from previous location"
                    } else {
                        "Old storage files removed from previous location"
                    }
        )

        val patternsRelocated =
            File(PATTERNS_FOLDER).exists() &&
                    File(PATTERNS_FOLDER).isDirectory &&
                    File(PLUGIN_DATA_FOLDER)
                        .listFiles { _, name -> name.matches(PATTERNS_TEMPLATE) }
                        .isNullOrEmpty()

        logger.debug(
            "[0.2.3 migration] " +
                    if (!patternsRelocated) {
                        "Patterns not relocated"
                    } else {
                        "Patterns relocated"
                    }
        )

        val oldPatternsRemoved = File(PLUGIN_DATA_FOLDER)
            .listFiles { _, name -> name.matches(PATTERNS_TEMPLATE) }
            .isNullOrEmpty()

        logger.debug(
            "[0.2.3 migration] " +
                    if (!oldPatternsRemoved) {
                        "Patterns not removed from previous location"
                    } else {
                        "Patterns removed from previous location"
                    }
        )

        val isDataStorageActual = isDataStorageActual(
            File(
                (if (dataFilesRelocated) STORAGE_FOLDER else STORAGE_OLD_FOLDER) + "/$STORAGE_USER_DATA_FILE"
            ),
            File("$PLUGIN_DATA_FOLDER/config.yml")
        )

        return dataFilesRelocated &&
                oldDataFilesRemoved &&
                patternsRelocated &&
                oldPatternsRemoved &&
                isDataStorageActual
    }

    /**
     * Checks if the secure data storage content is in actual version
     *
     * @param storageFile   Secure data storage file
     * @param configFile    Plugin configuration file
     * @return              *true* if the version is 0.2.3, *else* otherwise
     */
    private fun isDataStorageActual(storageFile: File, configFile: File): Boolean {
        if (!storageFile.exists()) return true   //  Because if there's no config file, it IS in correct format :-)
        if (!configFile.exists()) return false   //  Impossible to decode the storage without knowing the password

        val rawStorage = decodeStorage(storageFile, configFile) //  Intentionally no error handler to stop the migration
        val rawJson = try {
            gson.fromJson(rawStorage, JsonObject::class.java)
        } catch (j: JsonSyntaxException) {
            logger.error("Malformed secure storage:\n$rawStorage")
            throw j
        }

        //  Empty storage
        if (rawJson.size() == 0) {
            //  Empty storage can be correct storage
            return true
        }

        //  If there are no entries, storage is valid
        val (_, mapping) = rawJson.entrySet().first() ?: return true

        if (!mapping.isJsonObject) {
            logger.warn("Unrecognized secure data storage structure. Probably, the file is corrupted or has the later version. Contact the developer for assistance.")
            return false
        }

        //  Storage version 0.2.2
        if (mapping.asJsonObject.has("firstNotice")) return false

        return try {
            gson.fromJson(mapping, UserMappings::class.java) is UserMappings
        } catch (e: Exception) {
            logger.warn("Unrecognized user mappings structure. Probably, it has the later version. Contact the developer for assistance.")
            false
        }
    }

    private fun upgradeDataStorage(storageFile: File, configFile: File): Boolean {
        //  No need to perform upgrade if everything is ok already
        if (isDataStorageActual(storageFile, configFile)) return true

        logger.info("Upgrading the data storage to version 0.2.3...")

        //  Intentionally no error handler to stop the migration in case of error
        val rawStorage = decodeStorage(storageFile, configFile)
        val mapType_0_2_2 = object : TypeToken<MutableMap<String, UserMappings_0_2_2>>() {}.type
        val dataInOldFormat: MutableMap<String, UserMappings_0_2_2> = Gson().fromJson(rawStorage, mapType_0_2_2)
        val dataInNewFormat = dataInOldFormat.map { (id, mapping) ->
            id to UserMappings(
                mapping.addresses,
                UserNotificationsStatus(welcomeMessage = mapping.firstNotice)
            )
        }.toMap().toMutableMap()

        return encodeAndDumpStorage(storageFile, configFile, gson.toJson(dataInNewFormat))
    }

    /**
     * Decodes secure storage and returns decrypted content
     *
     * @param storageFile   Secure storage file
     * @param configFile    Plugin configuration file
     * @return              Decoded content
     */
    @Throws(IllegalArgumentException::class, SecurityException::class)
    private fun decodeStorage(storageFile: File, configFile: File): String {
        val password = getPasswordFromConfig(configFile)
            ?: throw IllegalArgumentException("Cannot find storage password in config file ${configFile.absolutePath}")
        val salt = getSaltFromConfig(configFile) ?: throw IllegalArgumentException("Cannot find salt in config file ${configFile.absolutePath}")

        ChaCha20Poly1305.salt = salt
        val cipher = ChaCha20Poly1305()

        val data = storageFile.readBytes().toHex()

        return try {
            cipher.decryptStringWithPassword(data, password)
        } catch (e: Exception) {
            throw SecurityException("Wrong password provided for security storage. ${e.message}\n" + e.stackTraceToString())
        }
    }

    /**
     * Encodes the data and writes the encrypted version to the storage file
     *
     * @param storageFile   Secure storage file
     * @param configFile    Plugin configuration file
     * @param data          Data to encode
     * @return              *true* if dumping was successful, throws security error otherwise
     */
    @Throws(IllegalArgumentException::class, SecurityException::class)
    private fun encodeAndDumpStorage(storageFile: File, configFile: File, data: String): Boolean {
        val password = getPasswordFromConfig(configFile)
            ?: throw IllegalArgumentException("Cannot find storage password in config file")
        val salt = getSaltFromConfig(configFile) ?: throw IllegalArgumentException("Cannot find salt in config file")

        ChaCha20Poly1305.salt = salt
        val cipher = ChaCha20Poly1305()

        return try {
            val encryptedData = cipher.encryptStringWithPassword(data, password).hexToByteArray()
            if (!storageFile.exists()) storageFile.createNewFile()
            storageFile.setWritable(true)
            storageFile.writeBytes(encryptedData)
            true
        } catch (e: Exception) {
            throw SecurityException("Wrong password provided for security storage. ${e.message}\n" + e.stackTraceToString())
        }
    }

    private fun getPasswordFromConfig(configFile: File): String? =
        getDataFromConfigFile(configFile, "^\\s*password:\\s*(.*)\$".toRegex(RegexOption.MULTILINE))

    private fun getSaltFromConfig(configFile: File): String? =
        getDataFromConfigFile(configFile, "^\\s*salt:\\s*(.*)\$".toRegex(RegexOption.MULTILINE))

    private fun getDataFromConfigFile(configFile: File, pattern: Regex): String? {
        if (!configFile.exists()) return null
        val config = configFile.readText()
        return pattern.find(config)?.groups?.let {
            if (it.isNotEmpty()) {
                it[1]?.value
            } else null
        }
    }
}
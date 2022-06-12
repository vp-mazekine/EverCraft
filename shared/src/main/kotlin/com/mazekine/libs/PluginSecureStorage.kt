package com.mazekine.libs

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.mazekine.everscale.EVER
import com.mazekine.libs.models.UserMappings
import com.mazekine.libs.models.UserMappings_0_2_2
import com.mazekine.libs.models.UserNotificationsStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom

object PluginSecureStorage {
    private val cipher = ChaCha20Poly1305()
    private val gson by lazy { Gson() }
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }
    private var keyStorageLoaded: Boolean = false
    private var dataStorageLoaded: Boolean = false

    var storagePassword: String = PLUGIN_NAME
    val initialized: Boolean
        get() = keyStorageLoaded && dataStorageLoaded

    init {
        Files.createDirectories(Paths.get(STORAGE_FOLDER))
    }

    private val keyStorageFile = File("$STORAGE_FOLDER/$STORAGE_PRIVATE_KEY_FILE")
    private var keyStorage: MutableMap<String, String> = mutableMapOf()

    private val dataStorageFile = File("$STORAGE_FOLDER/$STORAGE_USER_DATA_FILE")
    private var dataStorage: MutableMap<String, UserMappings> = mutableMapOf()

    /**
     * Loads the secure storage
     */
    fun init() {
        loadKeyStorage()
        loadDataStorage()
    }

    /**
     * Creates an empty storage if it doesn't exist
     *
     */
    private fun initKeyStorage() {
        if (!keyStorageFile.exists()) keyStorageFile.createNewFile()

        keyStorageFile.outputStream().let {
            it.write(
                cipher.encryptStringWithPassword("{}", storagePassword).hexToByteArray()
            )
            it.close()
        }
    }

    /**
     * Creates an empty storage if it doesn't exist
     *
     */
    private fun initDataStorage() {
        if (!dataStorageFile.exists()) dataStorageFile.createNewFile()

        dataStorageFile.outputStream().let {
            it.write(
                cipher.encryptStringWithPassword("{}", storagePassword).hexToByteArray()
            )
            it.close()
        }
    }

    /**
     * Loads key storage into memory
     *
     */
    private fun loadKeyStorage() {
        keyStorage = if (!keyStorageFile.exists()) {
            initKeyStorage()
            mutableMapOf()
        } else {
            val data = keyStorageFile.readBytes().toHex()
            val rawStorage = cipher.decryptStringWithPassword(data, storagePassword)
            val mapType = object : TypeToken<MutableMap<String, String>>() {}.type
            Gson().fromJson(rawStorage, mapType)
        }

        keyStorageLoaded = true
    }

    /**
     * Loads key storage into memory
     *
     */
    private fun loadDataStorage() {
        dataStorage = if (!dataStorageFile.exists()) {
            initDataStorage()
            mutableMapOf()
        } else {
            val data = dataStorageFile.readBytes().toHex()
            val rawStorage = cipher.decryptStringWithPassword(data, storagePassword)
            val mapType = object : TypeToken<MutableMap<String, UserMappings>>() {}.type
            Gson().fromJson(rawStorage, mapType)
        }

        dataStorageLoaded = true
    }

    /**
     * Initiates the key for a specified user.
     * Works only for those users who haven't set their private key yet.
     *
     * @param userId
     * @param key
     */
    fun setKey(userId: String, password: String, key: String? = null) {
        require(initialized) { "Secure storage was not properly initialized" }
        if (!keyStorage.containsKey(userId)) updateKey(userId, password, key)
    }

    /**
     * Updates user's key
     *
     * @param userId
     * @param password
     * @param privateKey
     */
    fun updateKey(userId: String, password: String, privateKey: String? = null) {
        require(initialized) { "Secure storage was not properly initialized" }

        val rawPrivateKey = privateKey ?: generatePrivateKey()
        val encryptedPK = cipher.encryptStringWithPassword(rawPrivateKey, password)

        keyStorage[userId] = encryptedPK

        dumpKeyStorage()
    }

    /**
     * Returns user's key
     *
     * @param userId
     * @return
     */
    fun getPrivateKey(userId: String): String? {
        require(initialized) { "Secure storage was not properly initialized" }

        return keyStorage[userId]
    }

    /**
     * Deletes user's data from the storage
     *
     * @param userId
     */
    fun deleteAccount(userId: String) {
        require(initialized) { "Secure storage was not properly initialized" }

        keyStorage.remove(userId)
        dumpKeyStorage()
    }

    /**
     * Saves key storages to the disk in an encrypted file
     *
     */
    private fun dumpKeyStorage() {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!keyStorageFile.exists()) keyStorageFile.createNewFile()

        keyStorageFile.writeBytes(
            cipher.encryptStringWithPassword(
                Gson().toJson(keyStorage),
                storagePassword
            ).hexToByteArray()
        )
    }

    /**
     * Saves data storage to the disk in an encrypted file
     *
     */
    private fun dumpDataStorage() {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!dataStorageFile.exists()) dataStorageFile.createNewFile()

        dataStorageFile.writeBytes(
            cipher.encryptStringWithPassword(
                gson.toJson(dataStorage),
                storagePassword
            ).hexToByteArray()
        )
    }

    /**
     * Generates a new private key
     *
     * @return
     */
    fun generatePrivateKey(): String {
        return Ed25519PrivateKeyParameters(SecureRandom()).encoded.toHex()
    }

    /**
     * Derives public key from a private one
     *
     * @param privateKeyHex
     * @return
     */
    fun derivePublicKey(privateKeyHex: String): String {
        val pkRebuild = Ed25519PrivateKeyParameters(privateKeyHex.hexToByteArray(), 0)
        return pkRebuild.generatePublicKey().encoded.toHex()
    }

    /**
     * Updates user password
     *
     * @param userId    Unique user id
     * @param oldPassword   Old user password
     * @param newPassword   New user password
     * @return  True if password was successfully changes, Else otherwise
     */
    fun updatePassword(userId: String, oldPassword: String, newPassword: String): Boolean {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!keyStorage.containsKey(userId)) return false

        return try {
            val rawPk = cipher.decryptStringWithPassword(keyStorage[userId]!!, oldPassword)
            keyStorage[userId] = cipher.encryptStringWithPassword(rawPk, newPassword)
            true
        } catch (e: Exception) {
            //  Unsuccessful decrypting
            false
        }
    }

    private fun initPlayerData(id: String): UserMappings? {
        require(initialized) { "Secure storage was not properly initialized" }

        dataStorage[id] = UserMappings()
        dumpDataStorage()
        return dataStorage[id]
    }

    fun findAddressByPlayerId(id: String): String? {
        require(initialized) { "Secure storage was not properly initialized" }
        return dataStorage[id]?.addresses?.firstOrNull()
    }

    fun findPlayerIdByAddress(address: String): String? {
        require(initialized) { "Secure storage was not properly initialized" }
        return dataStorage.filter { it.value.addresses.contains(address) }.keys.firstOrNull()
    }

    /**
     * Indicates if the player has received the welcome message
     *
     * @param id    User id
     * @return      *true* if welcome message was shown to user, *false* otherwise
     */
    fun getPlayerWelcomeNotification(id: String): Boolean? {
        require(initialized) { "Secure storage was not properly initialized" }

        return dataStorage[id]?.notifications?.welcomeMessage
    }

    fun setPlayerWelcomeNotification(id: String, flag: Boolean) {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!dataStorage.containsKey(id)) initPlayerData(id)
        dataStorage[id]?.notifications?.welcomeMessage = flag
        dumpDataStorage()
    }

    fun getWalletUpgradeRequiredNotification(id: String): Boolean? {
        require(initialized) { "Secure storage was not properly initialized" }

        return dataStorage[id]?.notifications?.walletUpgradeRequired
    }

    fun setWalletUpgradeRequiredNotification(id: String, flag: Boolean) {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!dataStorage.containsKey(id)) initPlayerData(id)
        dataStorage[id]?.notifications?.walletUpgradeRequired = flag
        dumpDataStorage()
    }

    fun walletsToUpgrade(id: String): List<String> {
        require(initialized) { "Secure storage was not properly initialized" }

        val addresses = dataStorage[id]?.addresses ?: return emptyList()
        if (addresses.isEmpty()) return emptyList()

        val result: MutableList<String> = mutableListOf()

        //logger.info("Obtaining wallets to upgrade for user $id...")
        runBlocking {
            val request = launch {
                addresses.forEachIndexed { index, address ->
                    launch {
                        //logger.info("Retrieving metadata for address $address...")
                        EVER.getAddressInfo(address)?.let {
/*
                            logger.info(
                                "\nAddress:   \t$address\n" +
                                "Custodians:\t${it.custodians}\n" +
                                "Confirms:  \t${it.confirmations}"
                            )
*/
                            if (it.custodians == 2 && it.confirmations == 2) result.add(address)
                        }
                    }
                }
            }

            request.join()
        }

/*
        logger.info("Wallets to upgrade: " + gson.toJson(result))
*/
        return result
    }

    fun setPlayerAddress(id: String, address: String) {
        require(initialized) { "Secure storage was not properly initialized" }

        if (dataStorage[id] == null) initPlayerData(id)
        dataStorage[id]!!.addresses.let {
            if (!it.contains(address)) it.add(address)
        }
        dumpDataStorage()
    }

    fun getPlayerAddress(id: String, index: Int? = null): String? {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!dataStorage.containsKey(id)) {
            initPlayerData(id)
            return null
        }

        return when {
            index == null -> dataStorage[id]!!.addresses.last()
            index < 0 -> null
            else -> dataStorage[id]!!.addresses[index]
        }
    }

    fun deletePlayerAddress(id: String, address: String): Boolean? {
        require(initialized) { "Secure storage was not properly initialized" }

        if (!dataStorage.containsKey(id)) return null
        return dataStorage[id]!!.addresses.remove(address)
    }

    fun getAllPlayersData(): MutableMap<String, UserMappings> = dataStorage

    fun unload() {
        dumpKeyStorage()
        dumpDataStorage()
        keyStorage = mutableMapOf()
        dataStorage = mutableMapOf()
    }
}

package com.mazekine.libs

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mazekine.libs.models.UserMappings
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bukkit.Bukkit
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom

object PluginSecureStorage {
    private val cipher = ChaCha20Poly1305()
    private val storagePassword by lazy {
        try {
            Bukkit.getPluginManager()
                .getPlugin("EverCraft")
                ?.config
                ?.get("storage.password")
                ?: "EverCraft"
        } catch (e: Exception) {
            "EverCraft"
        }
    }
    private val gson by lazy { Gson() }

    init {
        Files.createDirectories(Paths.get("evercraft"))
    }

    private val keyStorageFile = File("evercraft/pk.dat")
    private var keyStorage: MutableMap<String, String> = mutableMapOf()

    private val dataStorageFile = File("evercraft/ud.dat")
    private var dataStorage: MutableMap<String, UserMappings> = mutableMapOf()

    init {
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
                cipher.encryptStringWithPassword("{}", storagePassword as String).hexToByteArray()
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
                cipher.encryptStringWithPassword("{}", storagePassword as String).hexToByteArray()
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
            val rawStorage = cipher.decryptStringWithPassword(data, storagePassword as String)
            val mapType = object : TypeToken<MutableMap<String, String>>() {}.type
            Gson().fromJson(rawStorage, mapType)
        }
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
            val rawStorage = cipher.decryptStringWithPassword(data, storagePassword as String)
            val mapType = object : TypeToken<MutableMap<String, UserMappings?>>() {}.type
            Gson().fromJson(rawStorage, mapType)
        }
    }

    /**
     * Initiates the key for a specified user.
     * Works only for those users who haven't set their private key yet.
     *
     * @param userId
     * @param key
     */
    fun setKey(userId: String, password: String, key: String? = null) {
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
        return keyStorage[userId]
    }

    /**
     * Deletes user's data from the storage
     *
     * @param userId
     */
    fun deleteAccount(userId: String) {
        keyStorage.remove(userId)
        dumpKeyStorage()
    }

    /**
     * Saves key storages to the disk in an encrypted file
     *
     */
    private fun dumpKeyStorage() {
        if (!keyStorageFile.exists()) keyStorageFile.createNewFile()

        //  TODO: Add zipping to save disk space

        keyStorageFile.writeBytes(
            cipher.encryptStringWithPassword(
                Gson().toJson(keyStorage),
                storagePassword as String
            ).hexToByteArray()
        )
    }

    /**
     * Saves data storage to the disk in an encrypted file
     *
     */
    private fun dumpDataStorage() {
        if (!dataStorageFile.exists()) dataStorageFile.createNewFile()

        //  TODO:   Add zipping to save disk space

        dataStorageFile.writeBytes(
            cipher.encryptStringWithPassword(
                gson.toJson(dataStorage),
                storagePassword as String
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
        dataStorage[id] = UserMappings()
        dumpDataStorage()
        return dataStorage[id]
    }

    fun findAddressByPlayerId(id: String): String? = dataStorage[id]?.addresses?.firstOrNull()

    fun findPlayerIdByAddress(address: String): String? =
        dataStorage.filter { it.value.addresses.contains(address) }.keys.firstOrNull()

    fun getPlayerFirstNotice(id: String): Boolean? = dataStorage[id]?.firstNotice

    fun setPlayerFirstNotice(id: String, flag: Boolean) {
        if (!dataStorage.containsKey(id)) initPlayerData(id)
        dataStorage[id]?.firstNotice = flag
        dumpDataStorage()
    }

    fun setPlayerAddress(id: String, address: String) {
        if (dataStorage[id] == null) initPlayerData(id)
        dataStorage[id]!!.addresses.let {
            if (!it.contains(address)) it.add(address)
        }
        dumpDataStorage()
    }

    fun getPlayerAddress(id: String, index: Int? = null): String? {
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

    fun getAllPlayersData(): MutableMap<String, UserMappings> = dataStorage

    fun unload() {
        dumpKeyStorage()
        dumpDataStorage()
        keyStorage = mutableMapOf()
        dataStorage = mutableMapOf()
    }
}

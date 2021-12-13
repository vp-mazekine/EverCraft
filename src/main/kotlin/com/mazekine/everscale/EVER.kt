package com.mazekine.everscale

import com.google.gson.Gson
import com.mazekine.everscale.models.*
import com.mazekine.libs.PluginLocale
import ee.nx01.tonclient.*
import ee.nx01.tonclient.abi.*
import ee.nx01.tonclient.net.ParamsOfWaitForCollection
import ee.nx01.tonclient.process.ParamsOfProcessMessage
import ee.nx01.tonclient.tvm.ParamsOfRunTvm
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object EVER {
    private var config: FileConfiguration? = null
    private var apiEndpoint: String? = System.getenv("EVERCRAFT_API_ENDPOINT")
    private var apiPrefix: String? = System.getenv("EVERCRAFT_API_PREFIX")
    private var apiKey: String? = System.getenv("EVERCRAFT_API_KEY")
    private var apiSecret: String? = System.getenv("EVERCRAFT_API_SECRET")

    private val abi by lazy { TonUtils.readAbi("safemultisig/SafeMultisigWallet.abi.json") }
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }
    private val gson by lazy { Gson() }

    /**
     * Ktor client
     */
    val tonApiClient by lazy {
        HttpClient(CIO) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.NONE
            }
            install(JsonFeature) {
                serializer = GsonSerializer()
            }
        }
    }

    /**
     * EVER client
     */
    val everClient by lazy {
        TonClient(TonClientConfig(NetworkConfig(serverAddress = "main.ton.dev")))
    }

    fun loadConfiguration(pluginConfig: FileConfiguration?) {
        config = pluginConfig ?: Bukkit.getPluginManager().getPlugin("EverCraft")?.config
        apiEndpoint = config?.getString("api.endpoint", "https://ton-api.broxus.com")
            ?: System.getenv("EVERCRAFT_API_ENDPOINT")
        apiPrefix = config?.getString("api.prefix", "/ton/v3")
            ?: System.getenv("EVERCRAFT_API_PREFIX")
        apiKey = config?.getString("api.key", null)
            ?: System.getenv("EVERCRAFT_API_KEY")
        apiSecret = config?.getString("api.secret", null)
            ?: System.getenv("EVERCRAFT_API_SECRET")
    }

    private fun apiIsConfigured(): Boolean =
        apiEndpoint != null &&
        apiPrefix   != null &&
        apiKey      != null &&
        apiSecret   != null

    /**
     * Prepare the signature for API calls
     *
     * @param path The path to the method in API, e.g. <pre>/address/check</pre>
     * @param body The body that will be sent to the method.
     * @return  Encoded signature
     */
    private fun sign(path: String, body: String? = null): Pair<String, Long>? {
        if(!apiIsConfigured()) {
            logger.error(
                "[sign] " +
                PluginLocale.getLocalizedError("error.tonapi.not_configured", colored = false)
            )
            return null
        }

        val nonce = System.currentTimeMillis()
        val cipher = Mac.getInstance("HmacSHA256")
        val secretSpec = SecretKeySpec(
            apiSecret!!.toByteArray(),
            "HmacSHA256"
        )

        cipher.init(secretSpec)

        return Pair(
            Base64
                .getEncoder()
                .encodeToString(
                    cipher.doFinal(
                        (nonce.toString() + path + (body ?: "")).toByteArray()
                    )
                ),
            nonce
        )
    }

    /**
     * Checks an address for validity
     *
     * @param address
     * @return
     */
    suspend fun checkAddress(address: String): Boolean? {
        if(!apiIsConfigured()) {
            logger.error(
                "[checkAddress] " +
                        PluginLocale.getLocalizedError("error.tonapi.not_configured", colored = false)
            )
            return null
        }

        val path = "$apiPrefix/address/check"
        val body = CheckAddressInput(address).toJson()
        val (signature, nonce) = sign(path, body) ?: return null

        val response = try {
            tonApiClient.post<CheckAddressOutput>(apiEndpoint + path) {
                headers {
                    append("Accept", "application/json")
                    append("Accept-Encoding", "gzip, deflate, br")
                    append("Connection", "keep-alive")
                    append("api-key", apiKey!!)
                    append("sign", signature)
                    append("timestamp", nonce.toString())
                }

                this.body = body
            }
        } catch (e: ResponseException) {
            logger.error(
                "[checkAddress] Error ${e.response.status.value}: ${e.response.status.description} when trying to validate address $address\n" +
                        e.response.readText()
            )
            return null
        } catch (e: Exception) {
            logger.error(
                "[checkAddress] Couldn't validate the EVER address $address\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
            return null
        }

        return response.data.valid
    }

    /**
     * Create new EVER address
     *
     * @param accountType
     * @param confirmations
     * @param custodians
     * @param custodiansPublicKeys
     * @param workchainId
     * @return
     */
    suspend fun createAddress(
        accountType: AccountType,
        confirmations: Int,
        custodians: Int,
        custodiansPublicKeys: List<String>,
        workchainId: Int = 0
    ): String? {
        if(!apiIsConfigured()) {
            logger.error(
                "[createAddress] " +
                        PluginLocale.getLocalizedError("error.tonapi.not_configured", colored = false)
            )
            return null
        }

        val path = "$apiPrefix/address/create"
        val body = CreateAddressInput(
            accountType, confirmations, custodians, custodiansPublicKeys, workchainId
        ).toJson()
        val (signature, nonce) = sign(path, body) ?: return null

        val response = try {
            tonApiClient.post<CreateAddressOutput>(apiEndpoint + path) {
                headers {
                    append("Accept", "application/json")
                    append("Accept-Encoding", "gzip, deflate, br")
                    append("Connection", "keep-alive")
                    append("api-key", apiKey!!)
                    append("sign", signature)
                    append("timestamp", nonce.toString())
                }

                this.body = body
            }
        } catch (e: ResponseException) {
            logger.error(
                "[checkAddress] Error ${e.response.status.value}: ${e.response.status.description} when trying to create address\n" +
                        e.response.readText()
            )
            return null
        } catch (e: Exception) {
            logger.error(
                "[checkAddress] Couldn't create the EVER address\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
            return null
        }

        return "${response.data.workchainId}:${response.data.hex}"
    }

    suspend fun getAddress(address: String): AddressBalanceOutputData? {
        if(!apiIsConfigured()) {
            logger.error(
                "[getAddress] " +
                        PluginLocale.getLocalizedError("error.tonapi.not_configured", colored = false)
            )
            return null
        }

        val path = "$apiPrefix/address/$address"
        val (signature, nonce) = sign(path) ?: return null
        return try {
            tonApiClient.get<AddressBalanceOutput>(apiEndpoint + path) {
                headers {
                    append("Accept", "application/json")
                    append("Accept-Encoding", "gzip, deflate, br")
                    append("Connection", "keep-alive")
                    append("api-key", apiKey!!)
                    append("sign", signature)
                    append("timestamp", nonce.toString())
                }
            }.data
        } catch (e: ResponseException) {
            logger.error(
                "[checkAddress] Error ${e.response.status.value}: ${e.response.status.description} when trying to get EVER address $address\n" +
                        e.response.readText()
            )
            null
        } catch (e: Exception) {
            logger.error(
                "[checkAddress] Couldn't get the EVER address $address\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
            null
        }
    }

    /**
     * Create transaction from the wallet
     *
     * @param fromAddress   Address to send from
     * @param toAddress     Address to send to
     * @param value         Amount in nanoevers
     * @param type          Use Normal
     * @param bounce        Flag to bounce the transaction
     * @param id            Id of transaction to use in future
     * @return
     */
    suspend fun createTransaction(
        fromAddress: String,
        toAddress: String,
        value: String = "",
        type: TransactionSendOutputType = TransactionSendOutputType.Normal,
        bounce: Boolean = false,
        id: UUID = UUID.randomUUID()
    ): Triple<String, String?, String?>? {
        if(!apiIsConfigured()) {
            logger.error(
                "[createTransaction] " +
                        PluginLocale.getLocalizedError("error.tonapi.not_configured", colored = false)
            )
            return null
        }

        val path = "$apiPrefix/transactions/create"

        value.toBigDecimalOrNull()?.let {
            if(it <= BigDecimal(0)) {
                logger.error("[createTransaction] Transaction value must be positive")
                return null
            }
        } ?: run {
            logger.error("[createTransaction] Transaction value cannot be empty")
            return null
        }

        val tx = SendTransactionData(type, toAddress, value)
        val body = SendTransactionInput(
            bounce,
            fromAddress,
            id,
            listOf(tx)
        ).toJson()
        val (signature, nonce) = sign(path, body) ?: return null

        val response = try {
            tonApiClient.post<TransactionOutput>(apiEndpoint + path) {
                headers {
                    append("Accept", "application/json")
                    append("Accept-Encoding", "gzip, deflate, br")
                    append("Connection", "keep-alive")
                    append("api-key", apiKey!!)
                    append("sign", signature)
                    append("timestamp", nonce.toString())
                }

                this.body = body
            }
        } catch (e: ResponseException) {
            logger.error(
                "[checkAddress] Error ${e.response.status.value}: ${e.response.status.description} when trying to send transaction of $value EVER from address $fromAddress to $toAddress\n" +
                        e.response.readText()
            )
            return null
        } catch (e: Exception) {
            logger.error(
                "[checkAddress] Error when trying to send transaction of $value EVER from address $fromAddress to $toAddress\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
            return null
        }

        if (response.data == null) {
            logger.error(
                "[checkAddress] Error while creating the transaction of $value EVER from address $fromAddress to $toAddress\nError message: ${response.errorMessage}"
            )
            return null
        }

        if (response.data.aborted) {
            logger.error(
                "[checkAddress] The transaction of $value EVER from address $fromAddress to $toAddress has been aborted"
            )
            return null
        }

        return Triple(
            response.data.id,
            response.data.messageHash,
            response.data.transactionHash
        )
    }

    /**
     * Get transaction details from API by one of identifiers
     *
     * @param txId  Transaction id
     * @param messageHash   Message hash
     * @param txHash    Transaction hash
     * @return
     */
    suspend fun getTransaction(
        txId: String? = null,
        messageHash: String? = null,
        txHash: String? = null
    ): TransactionOutputData? {
        if (txId == null && messageHash == null && txHash == null) return null

        if(!apiIsConfigured()) {
            logger.error(
                "[getTransaction] " +
                        PluginLocale.getLocalizedError("error.tonapi.not_configured", colored = false)
            )
            return null
        }

        val apiMethod: String
        val searchValue: String

        when {
            txId != null -> {
                apiMethod = "id"
                searchValue = txId
            }
            messageHash != null -> {
                apiMethod = "mh"
                searchValue = messageHash
            }
            txHash != null -> {
                apiMethod = "h"
                searchValue = txHash
            }
            else -> return null
        }

        val path = "$apiPrefix/transactions/$apiMethod/$searchValue"
        val (signature, nonce) = sign(path) ?: return null

        return try {
            tonApiClient.get<TransactionOutput>(apiEndpoint + path) {
                headers {
                    append("Accept", "application/json")
                    append("Accept-Encoding", "gzip, deflate, br")
                    append("Connection", "keep-alive")
                    append("api-key", apiKey!!)
                    append("sign", signature)
                    append("timestamp", nonce.toString())
                }
            }.data
        } catch (e: ResponseException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns safe multisig transactions present at the wallet
     *
     * @param address Address of the wallet
     * @return
     */
    suspend fun getSafeMultisigTransactions(address: String): List<SafeMultisigTransaction> {
        //  If the address is on API and have the UnInit state, return an empty list
        if(getAddress(address)?.accountStatus == AccountStatus.UnInit) return emptyList()

        val message = ParamsOfEncodeMessage(
            abi = abi,
            address = address,
            callSet = CallSet(
                "getTransactions",
                input = mapOf()
            ),
            signer = Signer.none()
        )

        val encodedMessage = everClient.abi.encodeMessage(message).message

        val paramsOfWaitCollection = ParamsOfWaitForCollection(
            "accounts",
            mapOf("id" to mapOf("eq" to address)),
            "boc",
            null
        )

        val boc = try {
            Gson().fromJson(
                everClient.net.waitForCollection(paramsOfWaitCollection),
                ContractBocOutput::class.java
            ).result.boc
        } catch (e: Exception) {
            logger.error("[getSafeMultisigTransactions] Error while getting boc")
            return emptyList()
        }

        val params = ParamsOfRunTvm(
            encodedMessage,
            boc,
            abi = abi
        )

        val response = try {
            everClient.tvm.runTvm(params)
        } catch (e: Exception) {
            logger.error("[getSafeMultisigTransactions] Error while getting boc. Probably the account is not initialized")
            return emptyList()
        }

        response.decoded?.output?.let { txs ->
            return try {
                val transactions = gson.fromJson(
                    gson.toJson(txs),
                    SafeMultisigTransactions::class.java
                )
                transactions.transactions?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                logger.error(
                    "[getSafeMultisigTransactions] Got an exception while retrieving multisig transactions\n" +
                            e.message + "\n" +
                            e.stackTrace.joinToString("\n")
                )
                listOf()
            }
        } ?: run {
            return listOf()
        }
    }

    suspend fun createTransactionToSignOnMultisig(
        fromAddress: String,
        toAddress: String,
        value: String = "",
        type: TransactionSendOutputType = TransactionSendOutputType.Normal,
        bounce: Boolean = false,
        id: UUID = UUID.randomUUID()
    ): TransactionToSign? {
        //  Get current transactions from multisig
        val oldMsigTxList = getSafeMultisigTransactions(fromAddress)

        //  Try to find earlier initiated transaction with the same parameters
        oldMsigTxList
            .filter { it.value == value }
            .filter { it.dest == toAddress }
            .maxByOrNull { it.id }
            ?.id
            ?.let {
                return TransactionToSign(it)
            }

        //  SafeMultisig can have no more than 5 simultaneously initiated transactions
        if (oldMsigTxList.size == 5) {
            logger.warn("[EverCraft] Multisig $fromAddress is overcrowded with unfinished transactions. Please try again later")
            return null
        }

        //  Create transaction
        val txData = createTransaction(
            fromAddress, toAddress, value, type, bounce, id
        ) ?: return null

        val (txId, msgHash, txHash) = txData

        //  Make sure that the transaction was created in the wallet
        var tx = getTransaction(txId = txId)
        while (true) {
            when (tx?.status) {
                TransactionStatus.New -> {
                    /* no-op */
                }
                TransactionStatus.Done -> break     //  Continue execution
                TransactionStatus.PartiallyDone, TransactionStatus.Error, null -> {
                    logger.error(
                        "[createTransactionToSignOnMultisig] Unexpected answer from TON API\n$tx"
                    )
                    return null
                }
            }
            delay(1000)
            tx = getTransaction(txId = txId)
        }

        //  Get new msig transactions
        val newMsigTxList = getSafeMultisigTransactions(fromAddress)

        //  In case the transaction wasn't created, return error
        if(oldMsigTxList == newMsigTxList) return null

        //  Get the new transactions list
        val diffMsigTxList = newMsigTxList - oldMsigTxList

        val msigTxId = diffMsigTxList
            .filter { it.value == value }
            .filter { it.dest == toAddress }
            .maxByOrNull { it.id }
            ?.id

        return TransactionToSign(
            msigTxId,
            txId,
            msgHash,
            txHash
        )
    }

    suspend fun confirmSafeMultisigTransaction(
        address: String,
        msigTxId: String,
        keyPair: KeyPair
    ): Boolean {
        val message = ParamsOfEncodeMessage(
            abi = abi,
            address = address,
            callSet = CallSet(
                "confirmTransaction",
                input = mapOf("transactionId" to msigTxId)
            ),
            signer = Signer(
                keys = keyPair
            )
        )

        return try {
            val result = everClient.processing.processMessage(ParamsOfProcessMessage(message))
            return result.transaction.action?.success == true
        } catch (e: Exception) {
            false
        }
    }
}
package com.mazekine.everscale

import com.google.gson.Gson
import com.mazekine.everscale.models.*
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
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.jvm.Throws

object EVER {
    private var config: APIConfig? = null
    private var apiEndpoint: String? = System.getenv("EVERCRAFT_API_ENDPOINT")
    private var apiPrefix: String? = System.getenv("EVERCRAFT_API_PREFIX")
    private var apiKey: String? = System.getenv("EVERCRAFT_API_KEY")
    private var apiSecret: String? = System.getenv("EVERCRAFT_API_SECRET")

    private val abi by lazy { TonUtils.readAbi("safemultisig/SafeMultisigWallet.abi.json") }
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }
    private val gson by lazy { Gson() }
    private val locale: ResourceBundle = ResourceBundle.getBundle("messages", Locale.ENGLISH)

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

    /**
     * Load Ever API configuration
     *
     * @param apiConfig API configuration (all fields are required)
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    fun loadConfiguration(apiConfig: APIConfig) {
        config = apiConfig

        apiEndpoint = requireNotNull(config?.endpoint) { "Ever API endpoint is not set. Example: \"https://ton-api.broxus.com\"" }
        apiPrefix = requireNotNull(config?.prefix) { "Ever API prefix is not set. Example: \"/ton/v3\"" }
        apiKey = requireNotNull(config?.key) { "Ever API key is not set" }
        apiSecret = requireNotNull(config?.secret) { "Ever API secret is not set" }
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
                        getLocalizedMessage("error.tonapi.not_configured")
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
                        getLocalizedMessage("error.tonapi.not_configured")
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
                        getLocalizedMessage("error.tonapi.not_configured")
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
                        getLocalizedMessage("error.tonapi.not_configured")
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
        id: UUID = UUID.randomUUID(),
        onFail: ((TransactionFailReason) -> Unit)? = null
    ): Triple<String, String?, String?>? {
        if(!apiIsConfigured()) {
            logger.error(
                "[createTransaction] " +
                        getLocalizedMessage("error.tonapi.not_configured")
            )
            onFail?.let { it(TransactionFailReason.EVER_API_NOT_CONFIGURED) }
            return null
        }

        val path = "$apiPrefix/transactions/create"

        value.toBigDecimalOrNull()?.let {
            if(it <= BigDecimal(0)) {
                logger.error("[createTransaction] Transaction value must be positive")
                onFail?.let { it(TransactionFailReason.TX_VALUE_NOT_POSITIVE) }
                return null
            }
        } ?: run {
            logger.error("[createTransaction] Transaction value cannot be empty")
            onFail?.let { it(TransactionFailReason.TX_VALUE_EMPTY) }
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
                "[createTransaction] Error ${e.response.status.value}: ${e.response.status.description} when trying to send transaction of $value EVER from address $fromAddress to $toAddress\n" +
                        e.response.readText()
            )
            onFail?.let { it(
                when {
                    e.response.readText().contains("Invalid value") -> TransactionFailReason.TX_VALUE_EMPTY
                    e.response.readText().contains("Insufficient balance") -> TransactionFailReason.INSUFFICIENT_BALANCE
                    else -> TransactionFailReason.OTHER
                }
            ) }
            return null
        } catch (e: Exception) {
            logger.error(
                "[createTransaction] Error when trying to send transaction of $value EVER from address $fromAddress to $toAddress\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
            onFail?.let { it(
                when {
                    (e.message ?: "").contains("Invalid value") -> TransactionFailReason.TX_VALUE_EMPTY
                    (e.message ?: "").contains("Insufficient balance") -> TransactionFailReason.INSUFFICIENT_BALANCE
                    else -> TransactionFailReason.OTHER
                }
            ) }
            return null
        }

        if (response.data == null) {
            logger.error(
                "[checkAddress] Error while creating the transaction of $value EVER from address $fromAddress to $toAddress\nError message: ${response.errorMessage}"
            )
            onFail?.let { it(
                when {
                    response.errorMessage.contains("Invalid value") -> TransactionFailReason.TX_VALUE_EMPTY
                    response.errorMessage.contains("Insufficient balance") -> TransactionFailReason.INSUFFICIENT_BALANCE
                    else -> TransactionFailReason.OTHER
                }
            ) }
            return null
        }

        if (response.data.aborted) {
            logger.error(
                "[createTransaction] The transaction of $value EVER from address $fromAddress to $toAddress has been aborted"
            )
            onFail?.let { it(TransactionFailReason.TX_ABORTED) }
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
                        getLocalizedMessage("error.tonapi.not_configured")
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
        id: UUID = UUID.randomUUID(),
        onFail: ((TransactionFailReason) -> Unit)? = null
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
            onFail?.let { it(TransactionFailReason.TOO_MANY_UNFINISHED_TXS) }
            return null
        }

        //  Create transaction
        val txData = createTransaction(
            fromAddress, toAddress, value, type, bounce, id, onFail
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
                    onFail?.let { it(TransactionFailReason.OTHER_API_ERROR) }
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
        val diffMsigTxList = newMsigTxList - oldMsigTxList.toSet()

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

    private fun getLocalizedMessage(key: String): String {
        return try {
            locale.getString(key)
        } catch (e: Exception) {
            key
        } catch (t: Throwable) {
            key
        }
    }

    enum class TransactionFailReason {
        EVER_API_NOT_CONFIGURED,
        OTHER_API_ERROR,
        INSUFFICIENT_BALANCE,
        TX_VALUE_EMPTY,
        TX_VALUE_NOT_POSITIVE,
        TX_ABORTED,
        TOO_MANY_UNFINISHED_TXS,
        OTHER
    }
}
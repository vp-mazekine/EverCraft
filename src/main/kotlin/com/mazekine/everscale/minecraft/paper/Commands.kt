package com.mazekine.everscale.minecraft.paper

import com.google.gson.Gson
import com.mazekine.everscale.EVER
import com.mazekine.everscale.models.AccountType
import com.mazekine.libs.ChaCha20Poly1305
import com.mazekine.libs.PluginLocale
import com.mazekine.libs.PluginSecureStorage
import ee.nx01.tonclient.abi.KeyPair
import kotlinx.coroutines.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommandYamlParser
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/**
 * Sends a transfer to another player
 *
 * @constructor Create empty E send command
 */
class ESendCommand : CommandExecutor, PlayerSendable() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        GlobalScope.launch {
            withdraw(
                player,
                IPlayerSendable.SendType.Send,
                args
            )
        }

        return true
    }

}

/**
 * e_pk command to output current user's private key
 *
 * @constructor Create empty E p k command
 */
class EPKCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        if (args.isEmpty()) {
            player.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.password.missing")
            )
            return false
        }

        if (args.size > 1) {
            player.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.wrong_number", arrayOf(1))
            )
            return false
        }

        val encryptedPk = PluginSecureStorage.getPrivateKey(player.uniqueId.toString())

        //  If the player hasn't created a PK yet
        if (encryptedPk == null) {
            player.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.account.not_created")
            )
            return true
        }

        val pk = try {
            ChaCha20Poly1305().decryptStringWithPassword(encryptedPk, args[0])
        } catch (e: Exception) {
            player.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.password.wrong")
            )
            return false
        }

        player.sendMessage(
            TextComponent(PluginLocale.prefixRegular).apply {
                this.addExtra(
                    PluginLocale.getLocalizedMessage("pk.display.1")
                )
                this.addExtra(
                    TextComponent(pk).apply {
                        this.clickEvent = ClickEvent(
                            ClickEvent.Action.COPY_TO_CLIPBOARD,
                            pk
                        )
                        this.color = net.md_5.bungee.api.ChatColor.GREEN
                    }
                )
                this.addExtra("\n")
                this.addExtra(
                    PluginLocale.getLocalizedError("pk.display.2")
                )
            }
        )

        return true
    }

}

/**
 * e_register command for creating a new key
 *
 * @constructor Create empty E register command
 */
class ERegisterCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        return when (args.size) {
            0 -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.password.missing")
                )
                false
            }
            1 -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.password.repeat")
                )
                false
            }
            2 -> {
                if (args[0] == args[1]) {
                    PluginSecureStorage.setKey(
                        player.uniqueId.toString(),
                        args[0]
                    )

                    val encryptedPrivateKey = PluginSecureStorage.getPrivateKey(player.uniqueId.toString()) ?: run {
                        player.sendMessage(
                            PluginLocale.prefixError +
                                    PluginLocale.getLocalizedError("error.account.pk.not_saved")
                        )
                        return false
                    }
                    val privateKey = ChaCha20Poly1305().decryptStringWithPassword(encryptedPrivateKey, args[0])
                    val publicKey = PluginSecureStorage.derivePublicKey(privateKey)

                    val address = runBlocking {
                        EVER.createAddress(
                            AccountType.SafeMultisig,
                            2,
                            2,
                            listOf(publicKey)
                        )
                    } ?: run {
                        player.sendMessage(
                            PluginLocale.prefixError +
                                    PluginLocale.getLocalizedError("error.account.address.not_saved")
                        )
                        return false
                    }

                    PluginSecureStorage.setPlayerAddress(player.uniqueId.toString(), address)

                    player.sendMessage(
                        TextComponent(PluginLocale.prefixRegular).apply {
                            this.addExtra(
                                TextComponent(PluginLocale.getLocalizedMessage("account.status.success"))
                            )
                            this.addExtra(
                                TextComponent(address).apply {
                                    this.clickEvent = ClickEvent(
                                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                                        address
                                    )
                                    this.color = net.md_5.bungee.api.ChatColor.GREEN
                                }
                            )
                        }
                    )

                    true
                } else {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError("error.args.password.no_match")
                    )
                    false
                }
            }
            else -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.wrong_number", arrayOf(2))
                )
                false
            }
        }
    }

}

/**
 * e_new_password command for updating the wallet password
 *
 * @constructor Create empty E new password command
 */
class ENewPasswordCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        return when (args.size) {
            0 -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.password.missing.old")
                )
                false
            }
            1 -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.password.missing.new")
                )
                false
            }
            2 -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.password.repeat.new")
                )
                false
            }
            3 -> {
                val encryptedPk = PluginSecureStorage.getPrivateKey(player.uniqueId.toString())

                //  If the player hasn't created a PK yet
                if (encryptedPk == null) {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError("error.account.not_created")
                    )
                    return true
                }

                val pk = try {
                    ChaCha20Poly1305().decryptStringWithPassword(encryptedPk, args[0])
                } catch (e: Exception) {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError("error.args.password.wrong")
                    )
                    return false
                }

                if (args[1] == args[2]) {
                    PluginSecureStorage.updateKey(
                        player.uniqueId.toString(),
                        args[1],
                        pk
                    )

                    player.sendMessage(
                        PluginLocale.prefixRegular +
                                PluginLocale.getLocalizedMessage("password.status.success.new")
                    )
                    true
                } else {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError("error.args.password.no_match")
                    )
                    false
                }
            }
            else -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.wrong_number", arrayOf(3))
                )
                false
            }
        }
    }

}

/**
 * e_balance command to return player's balance in EVER
 *
 * @constructor Create empty E balance command
 */
class EBalanceCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        if (PluginSecureStorage.getPrivateKey(player.uniqueId.toString()) == null) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.account.not_created")
            )
            return false
        }

        val address = PluginSecureStorage
            .findAddressByPlayerId(player.uniqueId.toString())
            ?: run {
                sender.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.account.no_address")
                )
                return false
            }

        return when (args.size) {
            0 -> {
                val playerBalanceNano = runBlocking {
                    EVER.getAddress(address)?.balance?.toBigDecimalOrNull()
                } ?: run {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError("error.account.balance.unavailable")
                    )
                    return false
                }

                val playerBalance = (playerBalanceNano.setScale(4, RoundingMode.HALF_DOWN) / BigDecimal(1_000_000_000))
                    .stripTrailingZeros()
                    .toPlainString()

                player.sendMessage(
                    TextComponent(PluginLocale.prefixRegular).apply {
                        this.addExtra(
                            TextComponent(
                                PluginLocale.getLocalizedMessage(
                                    "account.balance",
                                    arrayOf(
                                        playerBalance,
                                        PluginLocale.currencyName.toString()
                                    )
                                )
                            )
                        )
                    }
                )

                true
            }
            else -> {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.wrong_count")
                )
                false
            }
        }
    }
}

/**
 * e_withdraw command to send EVERs to an external address
 *
 * @constructor Create empty E withdraw command
 */
class EWithdrawCommand : CommandExecutor, PlayerSendable() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        withdraw(
            player,
            IPlayerSendable.SendType.Withdraw,
            args
        )

        return true
    }
}

class EAddressCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }

        val player: Player = sender

        val encryptedPrivateKey = PluginSecureStorage.getPrivateKey(player.uniqueId.toString())
        if (encryptedPrivateKey == null) {
            sender.sendMessage("${ChatColor.RED}[EverCraft] You haven't registered yet.\nPlease use the /e_register command to create a new account.")
            return false
        }

        var address = PluginSecureStorage
            .getPlayerAddress(player.uniqueId.toString())

        if (address == null) {
            if (args.isEmpty()) {
                sender.sendMessage("${ChatColor.RED}[EverCraft] You have no earlier created addresses. To create one, please input the password after the command name.")
                return false
            }

            val privateKey = ChaCha20Poly1305().decryptStringWithPassword(encryptedPrivateKey, args[0])
            val publicKey = PluginSecureStorage.derivePublicKey(privateKey)

            address = runBlocking {
                EVER.createAddress(
                    AccountType.SafeMultisig,
                    2,
                    2,
                    listOf(publicKey)
                )
            } ?: run {
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.account.address.not_saved")
                )
                return false
            }
        }

        PluginSecureStorage.setPlayerAddress(player.uniqueId.toString(), address)

        player.sendMessage(
            TextComponent(PluginLocale.prefixRegular).apply {
                this.addExtra(PluginLocale.getLocalizedMessage("account.address"))
                this.addExtra(TextComponent(address).apply {
                    this.color = net.md_5.bungee.api.ChatColor.GREEN
                    this.clickEvent = ClickEvent(
                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                        address
                    )
                })
            }
        )

        return true
    }

}

class EUserDataCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender is Player) return true

        println(Gson().toJson(PluginSecureStorage.getAllPlayersData()))
        return true
    }

}

class EVersionCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.access.player_only")
            )
            return false
        }
        val player: Player = sender
        player.sendMessage(
            PluginLocale.prefixRegular +
                    PluginLocale.getLocalizedMessage(
                        "version",
                        arrayOf(
                            Bukkit.getPluginManager()
                                .getPlugin("EverCraft")?.description?.version ?: "0.0.0"
                        )
                    )
        )

        return true
    }
}

interface IPlayerSendable {
    fun withdraw(
        player: Player,
        command: SendType,
        args: Array<out String>
    ): Boolean

    suspend fun withdraw(
        addressFrom: String,
        addressTo: String,
        amount: String,
        password: String,
        from: Player,
        to: Player? = null
    ): Boolean

    enum class SendType { Send, Withdraw }
}

open class PlayerSendable : IPlayerSendable {
    protected val logger by lazy { LoggerFactory.getLogger(this::class.java) }

    /**
     * Universal withdraw/send method
     *
     * @param player    Sender
     * @param command   Send type
     * @param args      Command inputs
     * @return True if all went smoothly, False otherwise
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun withdraw(
        player: Player,
        command: IPlayerSendable.SendType,
        args: Array<out String>
    ): Boolean {
        //  First check if the sender has the address created
        val addressFrom: String =
            PluginSecureStorage
                .findAddressByPlayerId(player.uniqueId.toString())
                ?: run {
                    if (player.isOnline) {
                        player.sendMessage(
                            PluginLocale.prefixError +
                                    PluginLocale.getLocalizedError("error.account.no_address")
                        )
                    }
                    return false
                }

        //  Check arguments quantity
        if (args.size == 1) {
            player.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.amount.absent")
            )
            return false
        }

        if (args.size == 2) {
            player.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.password.missing")
            )
            return false
        }

        if (args.size > 3) {
            if (player.isOnline)
                player.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.args.wrong_number", arrayOf(3))
                )
            return false
        }

        var receiver: Player? = null
        val addressTo = when (command) {
            IPlayerSendable.SendType.Withdraw -> args[0]
            IPlayerSendable.SendType.Send -> {
                //  Check if player exists
                receiver = Bukkit.getPlayerExact(args[0]) ?: run {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError("error.args.player.absent", arrayOf(args[0]))
                    )
                    return false
                }

                //  Check if the receiver has created an account
                PluginSecureStorage.findAddressByPlayerId(receiver.uniqueId.toString()) ?: run {
                    player.sendMessage(
                        PluginLocale.prefixError +
                                PluginLocale.getLocalizedError(
                                    "error.transfer.receiver.not_registered",
                                    arrayOf(args[0])
                                )
                    )
                    return false
                }
            }
        }

        val amount = args[1]


        val password = args[2]

        GlobalScope.launch {
            withdraw(
                addressFrom,
                addressTo,
                amount,
                password,
                player,
                receiver
            )
        }

        return true
    }

    override suspend fun withdraw(
        addressFrom: String,
        addressTo: String,
        amount: String,
        password: String,
        from: Player,
        to: Player?
    ): Boolean {
        //  Check that addresses don't match
        if (addressFrom == addressTo) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.transfer.receiver.self")
            )
            return false
        }

        //  Check if sender's address is correct
        val addressFromCorrect =
            withContext(Dispatchers.Default) { EVER.checkAddress(addressFrom) }

        if (addressFromCorrect != true) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.account.address.invalid")
            )
            return false
        }

        //  Check if receiver's address is correct
        val addressToCorrect =
            withContext(Dispatchers.Default) { EVER.checkAddress(addressTo) }

        if (addressToCorrect != true) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.address.invalid")
            )
            return false
        }

        //  Validate the amount
        val amountBD = amount.toBigDecimalOrNull() ?: run {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.amount.not_numeric")
            )
            return false
        }

        if (amountBD <= BigDecimal(0)) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.amount.not_positive")
            )
            return false
        }

        //  Check the amount is sufficient
        val fromBalanceNano = withContext(Dispatchers.Default) {
            EVER.getAddress(addressFrom)
                ?.balance
                ?.toBigDecimalOrNull()
        } ?: run {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError(
                            "error.account.balance.unavailable"
                        )
            )
            return false
        }

        val fromBalance = fromBalanceNano.setScale(4, RoundingMode.HALF_DOWN) / BigDecimal(1_000_000_000)

        if (amountBD > fromBalance) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError(
                            "error.account.balance.insufficient",
                            arrayOf(
                                amount,
                                PluginLocale.currencyName.toString()
                            )
                        )
            )

            return false
        }

        //  Check that private key exists and accessible
        val encryptedPrivateKey = PluginSecureStorage.getPrivateKey(from.uniqueId.toString())
            ?: run {
                from.sendMessage(
                    PluginLocale.prefixError +
                            PluginLocale.getLocalizedError("error.account.not_created")
                )
                return false
            }

        from.sendMessage(
            PluginLocale.prefixRegular +
                    PluginLocale.getLocalizedMessage("transfer.status.unpacking_keys")
        )

        val privateKey = try {
            ChaCha20Poly1305().decryptStringWithPassword(encryptedPrivateKey, password)
        } catch (e: Exception) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.args.password.wrong")
            )
            return false
        }

        val publicKey = PluginSecureStorage.derivePublicKey(privateKey)

        val message = try {
            PluginLocale.getLocalizedMessage("fun.creatures").split(',').let {
                when (it.size) {
                    0 -> PluginLocale.getLocalizedMessage("transfer.status.preparing")
                    else -> PluginLocale.getLocalizedMessage(
                        "transfer.status.preparing.creatures",
                        arrayOf(it[Random.nextInt(0, it.lastIndex)].trim())
                    )
                }
            }
        } catch (e: Exception) {
            PluginLocale.getLocalizedMessage("transfer.status.preparing")
        }

        from.sendMessage(
            PluginLocale.prefixRegular +
                    message
        )

        //  Creaing transaction on multisig
        val tx = withContext(Dispatchers.Default) {
            EVER.createTransactionToSignOnMultisig(
                fromAddress = addressFrom,
                toAddress = addressTo,
                value = (amountBD * BigDecimal(1_000_000_000)).setScale(0, RoundingMode.HALF_DOWN).toString()
            )
        } ?: run {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.transfer.tx.uninit")
            )
            return false
        }

        val (msigTxId, txId, _, _) = tx

        //  Check that transaction has created
        if (msigTxId == null) {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.transfer.tx.uninit")
            )
            return false
        }

        from.sendMessage(
            PluginLocale.prefixRegular +
                    PluginLocale.getLocalizedMessage("transfer.status.signing")
        )

        //  Check that signing has passed successfully
        val withdrawalResult = withContext(Dispatchers.Default) {
            EVER.confirmSafeMultisigTransaction(
                addressFrom,
                msigTxId,
                KeyPair(publicKey, privateKey)
            )
        }

        return if (withdrawalResult) {
            val fromMessage = TextComponent(PluginLocale.prefixRegular).apply {
                this.addExtra(
                    PluginLocale.getLocalizedMessage(
                        "transfer.status.success",
                        arrayOf(
                            amount,
                            PluginLocale.currencyName.toString(),
                            to?.name ?: addressTo
                        )
                    )
                )
            }

            logger.info(
                "Sending $amount ${PluginLocale.currencyName} to ${to?.name ?: addressTo}\n" + arrayOf(
                    amount,
                    PluginLocale.currencyName.toString(),
                    to?.name ?: addressTo
                ).toList()
            )

            val toMessage = TextComponent(
                PluginLocale.prefixRegular +
                        PluginLocale.getLocalizedMessage(
                            "transfer.incoming", arrayOf(
                                from.name,
                                amount,
                                PluginLocale.currencyName.toString()
                            )
                        )
            )

            //  Get transaction hash, if any
            withContext(Dispatchers.Default) {
                EVER.getTransaction(txId)
            }?.let {
                it.transactionHash?.let { hash ->
                    val txHash = TextComponent(PluginLocale.getLocalizedMessage("transfer.tx_hash") + " ")
                    txHash.addExtra(
                        TextComponent(hash).apply {
                            this.color = net.md_5.bungee.api.ChatColor.GREEN
                            this.clickEvent = ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                hash
                            )
                        })
                    fromMessage.addExtra(txHash)
                    toMessage.addExtra(txHash)
                }
            }

            from.sendMessage(fromMessage)
            to?.sendMessage(toMessage)

            true
        } else {
            from.sendMessage(
                PluginLocale.prefixError +
                        PluginLocale.getLocalizedError("error.unknown")
            )

            false
        }
    }
}
package com.mazekine.everscale.minecraft.paper

import com.google.gson.Gson
import com.mazekine.everscale.EVER
import com.mazekine.everscale.minecraft.paper.Store.countAll
import com.mazekine.everscale.minecraft.paper.Store.isStoreItem
import com.mazekine.everscale.models.AccountType
import com.mazekine.libs.ChaCha20Poly1305
import com.mazekine.libs.PluginLocale
import com.mazekine.libs.PluginSecureStorage
import ee.nx01.tonclient.abi.KeyPair
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
//import net.md_5.bungee.api.chat.ClickEvent
//import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
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
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
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
class EPKCommand : CommandExecutor, ICommunicative {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender

        if (args.isEmpty()) {
            _onFail(player, "error.args.password.missing")
            return false
        }

        if (args.size > 1) {
            _onFail(player, "error.args.wrong_number", arrayOf(1))
            return false
        }

        val encryptedPk = PluginSecureStorage.getPrivateKey(player.uniqueId.toString())

        //  If the player hasn't created a PK yet
        if (encryptedPk == null) {
            _onFail(player, "error.account.not_created")
            return true
        }

        val pk = try {
            ChaCha20Poly1305().decryptStringWithPassword(encryptedPk, args[0])
        } catch (e: Exception) {
            _onFail(player, "error.args.password.wrong")
            return false
        }

        player.sendMessage(
            PluginLocale.getLocalizedMessage("pk.display.1", prefix = true).apply {
                this.append(
                    Component.text("${ChatColor.GREEN}$pk").apply {
                        this.clickEvent(
                            ClickEvent.clickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                pk
                            )
                        )
                    }
                )
                this.append(Component.text("\n"))
                this.append(
                    PluginLocale.getLocalizedError("pk.display.2", prefix = true)
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
class ERegisterCommand : CommandExecutor, ICommunicative {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = false)
            )
            return false
        }

        val player: Player = sender

        return when (args.size) {
            0 -> {
                _onFail(player, "error.args.password.missing")
                false
            }
            1 -> {
                _onFail(player, "error.args.password.repeat")
                false
            }
            2 -> {
                if (args[0] == args[1]) {
                    PluginSecureStorage.setKey(
                        player.uniqueId.toString(),
                        args[0]
                    )

                    val encryptedPrivateKey = PluginSecureStorage.getPrivateKey(player.uniqueId.toString()) ?: run {
                        _onFail(player, "error.account.pk.not_saved")
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
                        _onFail(player, "error.account.address.not_saved")
                        return false
                    }

                    PluginSecureStorage.setPlayerAddress(player.uniqueId.toString(), address)

                    //  TODO:   Check that works after refactoring
                    player.sendMessage(
                        PluginLocale.getLocalizedMessage("account.status.success", prefix = true)
                            .append(
                                Component.text("${ChatColor.GREEN.asBungee()}$address").clickEvent(
                                    ClickEvent.clickEvent(
                                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                                        address
                                    )
                                )
                            )
                    )

                    true
                } else {
                    _onFail(player, "error.args.password.no_match")
                    false
                }
            }
            else -> {
                _onFail(player, "error.args.wrong_number", arrayOf(2))
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
class ENewPasswordCommand : CommandExecutor, ICommunicative {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender

        return when (args.size) {
            0 -> {
                _onFail(player, "error.args.password.missing.old")
                false
            }
            1 -> {
                _onFail(player, "error.args.password.missing.new")
                false
            }
            2 -> {
                _onFail(player, "error.args.password.repeat.new")
                false
            }
            3 -> {
                val encryptedPk = PluginSecureStorage.getPrivateKey(player.uniqueId.toString())

                //  If the player hasn't created a PK yet
                if (encryptedPk == null) {
                    _onFail(player, "error.account.not_created")
                    return true
                }

                val pk = try {
                    ChaCha20Poly1305().decryptStringWithPassword(encryptedPk, args[0])
                } catch (e: Exception) {
                    _onFail(player, "error.args.password.wrong")
                    return false
                }

                if (args[1] == args[2]) {
                    PluginSecureStorage.updateKey(
                        player.uniqueId.toString(),
                        args[1],
                        pk
                    )

                    _onSuccess(player, "password.status.success.new")
                    true
                } else {
                    _onFail(player, "error.args.password.no_match")
                    false
                }
            }
            else -> {
                _onFail(player, "error.args.wrong_number", arrayOf(3))
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
class EBalanceCommand : CommandExecutor, ICommunicative {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender

        if (PluginSecureStorage.getPrivateKey(player.uniqueId.toString()) == null) {
            _onFail(player, "error.account.not_created")
            return false
        }

        val address = PluginSecureStorage
            .findAddressByPlayerId(player.uniqueId.toString())
            ?: run {
                _onFail(player, "error.account.no_address")
                return false
            }

        return when (args.size) {
            0 -> {
                val playerBalanceNano = runBlocking {
                    EVER.getAddress(address)?.balance?.toBigDecimalOrNull()
                } ?: run {
                    _onFail(player, "error.account.balance.unavailable")
                    return false
                }

                val playerBalance = (playerBalanceNano.setScale(4, RoundingMode.HALF_DOWN) / BigDecimal(1_000_000_000))
                    .stripTrailingZeros()
                    .toPlainString()

                _onSuccess(
                    player, "account.balance",
                    arrayOf(
                        playerBalance,
                        PluginLocale.currencyName.toString()
                    )
                )
                true
            }
            else -> {
                _onFail(player, "error.args.wrong_count")
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

class EAddressCommand : CommandExecutor, ICommunicative {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender

        val encryptedPrivateKey = PluginSecureStorage.getPrivateKey(player.uniqueId.toString())
        if (encryptedPrivateKey == null) {
            _onFail(player, "error.account.not_created")
            return false
        }

        var address = PluginSecureStorage
            .getPlayerAddress(player.uniqueId.toString())

        if (address == null) {
            if (args.isEmpty()) {
                _onFail(player, "error.account.no_address")
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
                _onFail(player, "error.account.address.not_saved")
                return false
            }
        }

        PluginSecureStorage.setPlayerAddress(player.uniqueId.toString(), address)

        _onUpdate(
            player,
            PluginLocale.getLocalizedMessage("account.address", prefix = true)
                .append(
                    Component.text("${ChatColor.GREEN.asBungee()}$address")
                        .clickEvent(
                            ClickEvent.clickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                address
                            )
                        )
                )
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

class EVersionCommand : CommandExecutor, ICommunicative {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender
        _onUpdate(
            player,
            PluginLocale.getLocalizedMessage(
                "version",
                arrayOf(
                    Bukkit.getPluginManager()
                        .getPlugin("EverCraft")?.description?.version ?: "0.0.0"
                ),
                prefix = true
            )
        )

        return true
    }
}

class EStoreCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender

        Store.open(player)

        return true
    }
}

class ECouponCommand : CommandExecutor, PlayerSendable() {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (Store.storeWallet == null) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.store.not_configured", prefix = true)
            )
            return false
        }

        //  Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(
                PluginLocale.getLocalizedError("error.access.player_only", prefix = true)
            )
            return false
        }

        val player: Player = sender

        //  If no args specified, just output the coupon price
        if (args.isEmpty()) {
            _onSuccess(
                player, "store.coupon.price",
                arrayOf(Store.couponPrice, PluginLocale.currencyName ?: "EVER")
            )
            return true
        }

        val subcommand = args[0].lowercase()
        val amount = if (args.size > 1) args[1].toIntOrNull() else null
        val password = if (args.size > 2) args[2] else null

        //  If subcommand is unknown, output error
        if (subcommand != "price" && subcommand != "buy") {
            _onFail(player, "error.args.unknown")
            return false
        }

        //  If amount is specified, it must be positive
        amount?.let {
            if (it <= 0) {
                _onFail(player, "error.args.amount.not_positive")
                logger.info("Amount: $amount")
                return false
            }
        }

        //  Output coupon(s) price
        if (subcommand == "price") {
            when (amount) {
                null ->     //  A single coupon
                    _onSuccess(
                        player, "store.coupon.price",
                        arrayOf(
                            Store.couponPrice,
                            PluginLocale.currencyName ?: "EVER"
                        )
                    )
                else ->     //  Multiple coupons
                    _onSuccess(
                        player, "store.coupon.price.multiple",
                        arrayOf(
                            amount.toString(),
                            amount.toBigDecimal() * Store.couponPrice,
                            PluginLocale.currencyName ?: "EVER"
                        )
                    )
            }

            return true
        }

        if (subcommand == "buy") {
            //  Amount must exist and be valid
            if (amount == null) {
                _onFail(player, if (args.size == 1) "error.args.amount.absent" else "error.args.amount.not_numeric")
                return false
            }

            //  Password must exist
            if (password == null && !Store.couponPrice.equals(0)) {
                _onFail(player, "error.args.password.missing")
                return false
            }
        }

        val onSuccess: (Player?, String?, Array<out Any>?) -> Unit = { p, m, a ->
            p?.let {
                val initialQuantity = it.inventory.countAll { stack ->
                    stack?.isStoreItem(Store.Buttons.COUPON) == true
                }

                var remainingQuantity = amount ?: run {
                    if(m != null) _onFail(p, m, a)
                    _onFail(
                        it,
                        PluginLocale.getLocalizedError("error.store.purchase_error")
                    )
                    return@let
                }

                //  Try to find existing stack of coupons to add to it
                run delivery@{
                    it.inventory.forEach { stack ->
                        if (stack?.isStoreItem(Store.Buttons.COUPON) == true &&
                                stack.amount < stack.maxStackSize) {
                            val availableSlot = stack.maxStackSize - stack.amount
                            if(availableSlot < remainingQuantity) {
                                remainingQuantity -= availableSlot
                                stack.amount += availableSlot
                            } else {
                                stack.amount += remainingQuantity
                                remainingQuantity = 0
                                return@delivery
                            }
                        }
                    }
                }

                //  Create a new stack otherwise
                if(remainingQuantity != 0) {
                    it.inventory.addItem(
                        Store.coupon.asQuantity(remainingQuantity)
                    )
                }

                if (m != null) _onSuccess(it, m, a)
                _onUpdate(
                    it,
                    PluginLocale.getLocalizedMessage("store.coupon.purchased", arrayOf(amount!!), prefix = true)
                )
            } ?: run {
                logger.warn("Ghost purchase in the store: cannot identify purchaser")
            }
        }

        val onFail: (Player?, String?, Array<out Any>?) -> Unit = { p, m, a ->
            p?.let {
                if(m != null) _onFail(p, m, a)
                _onFail(
                    it,
                    PluginLocale.getLocalizedError("error.store.purchase_error")
                )
            }
        }

        if(Store.couponPrice == BigDecimal(0)) {
            onSuccess(player, null, null)
        } else {
            withdraw(
                player,
                IPlayerSendable.SendType.Withdraw,
                arrayOf(
                    Store.storeWallet!!,
                    (amount!!.toBigDecimal() * Store.couponPrice).toPlainString(),
                    password!!
                ),
                onSuccess = onSuccess,
                onFail = onFail,
                isStoreTx = true
            )
        }

        return true
    }

}

/**
 * Adds the opportunity to withdraw funds from a user's wallet to the specific command
 *
 * @constructor Create empty IPlayerSendable
 */
interface IPlayerSendable : ICommunicative {
    fun withdraw(
        player: Player,
        command: SendType,
        args: Array<out String>,
        onSuccess: ((Player?, String?, Array<out Any>?) -> Unit)? = ::_onSuccess,
        onFail: ((Player?, String?, Array<out Any>?) -> Unit)? = ::_onFail,
        onUpdate: ((Player?, Component?) -> Unit)? = ::_onUpdate,
        isStoreTx: Boolean = false
    ): Boolean

    suspend fun withdraw(
        addressFrom: String,
        addressTo: String,
        amount: String,
        password: String,
        from: Player,
        to: Player? = null,
        onSuccess: ((Player?, String?, Array<out Any>?) -> Unit)? = ::_onSuccess,
        onFail: ((Player?, String?, Array<out Any>?) -> Unit)? = ::_onFail,
        onUpdate: ((Player?, Component?) -> Unit)? = ::_onUpdate,
        isStoreTx: Boolean = false
    ): Boolean

    enum class SendType { Send, Withdraw }
}

/**
 * Introduces the user feedback communication
 *
 * @constructor Create empty ICommunicative
 */
interface ICommunicative {
    fun _onSuccess(player: Player? = null, message: String? = null, args: Array<out Any>? = null) {
        player?.let { p ->
            message?.let { m ->
                if (p.isOnline) p.sendMessage(PluginLocale.getLocalizedMessage(m, args, prefix = true))
            }
        }
    }

    fun _onUpdate(player: Player? = null, message: Component? = null) {
        player?.let { p ->
            message?.let { m ->
                if (p.isOnline) p.sendMessage(message)
            }
        }
    }

    fun _onFail(player: Player? = null, message: String? = null, args: Array<out Any>? = null) {
        player?.let { p ->
            message?.let { m ->
                if (p.isOnline) p.sendMessage(PluginLocale.getLocalizedError(m, args, prefix = true))
            }
        }
    }
}

/**
 * Implements IPlayerSendable
 *
 * @constructor Create empty PlayerSendable
 */
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
        args: Array<out String>,
        onSuccess: ((Player?, String?, Array<out Any>?) -> Unit)?,
        onFail: ((Player?, String?, Array<out Any>?) -> Unit)?,
        onUpdate: ((Player?, Component?) -> Unit)?,
        isStoreTx: Boolean
    ): Boolean {
        //  First check if the sender has the address created
        val addressFrom: String =
            PluginSecureStorage
                .findAddressByPlayerId(player.uniqueId.toString())
                ?: run {
                    if (onFail != null) onFail(player, "error.account.no_address", null)
                    return false
                }

        //  Check arguments quantity
        if (args.size == 1) {
            if (onFail != null) onFail(player, "error.args.amount.absent", null)
            return false
        }

        if (args.size == 2) {
            if (onFail != null) onFail(player, "error.args.password.missing", null)
            return false
        }

        if (args.isEmpty() || args.size > 3) {
            if (onFail != null) onFail(player, "error.args.wrong_number", arrayOf(3))
            return false
        }

        var receiver: Player? = null
        val addressTo = when (command) {
            IPlayerSendable.SendType.Withdraw -> args[0]
            IPlayerSendable.SendType.Send -> {
                //  Check if player exists
                receiver = Bukkit.getPlayerExact(args[0]) ?: run {
                    if (onFail != null) onFail(player, "error.args.player.absent", arrayOf(args[0]))
                    return false
                }

                //  Check if the receiver has created an account
                PluginSecureStorage.findAddressByPlayerId(receiver.uniqueId.toString()) ?: run {
                    if (onFail != null) onFail(player, "error.transfer.receiver.not_registered", arrayOf(args[0]))
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
                receiver,
                onSuccess,
                onFail,
                isStoreTx = isStoreTx
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
        to: Player?,
        onSuccess: ((Player?, String?, Array<out Any>?) -> Unit)?,
        onFail: ((Player?, String?, Array<out Any>?) -> Unit)?,
        onUpdate: ((Player?, Component?) -> Unit)?,
        isStoreTx: Boolean
    ): Boolean {
        //  Check that addresses don't match
        if (addressFrom == addressTo) {
            if (onFail != null) onFail(from, "error.transfer.receiver.self", null)
            return false
        }

        //  Check if sender's address is correct
        val addressFromCorrect =
            withContext(Dispatchers.Default) { EVER.checkAddress(addressFrom) }

        if (addressFromCorrect != true) {
            if (onFail != null) onFail(from, "error.account.address.invalid", null)
            return false
        }

        //  Check if receiver's address is correct
        val addressToCorrect =
            withContext(Dispatchers.Default) { EVER.checkAddress(addressTo) }

        if (addressToCorrect != true) {
            if (onFail != null) onFail(from, "error.args.address.invalid", null)
            return false
        }

        //  Validate the amount
        val amountBD = amount.toBigDecimalOrNull() ?: run {
            if (onFail != null) onFail(from, "error.args.amount.not_numeric", null)
            return false
        }

        if (amountBD <= BigDecimal(0)) {
            if (onFail != null) onFail(from, "error.args.amount.not_positive", null)
            return false
        }

        //  Check the amount is sufficient
        val fromBalanceNano = withContext(Dispatchers.Default) {
            EVER.getAddress(addressFrom)
                ?.balance
                ?.toBigDecimalOrNull()
        } ?: run {
            if (onFail != null) onFail(from, "error.account.balance.unavailable", null)
            return false
        }

        val fromBalance = fromBalanceNano.setScale(4, RoundingMode.HALF_DOWN) / BigDecimal(1_000_000_000)

        if (amountBD > fromBalance) {
            if (onFail != null) onFail(
                from,
                "error.account.balance.insufficient",
                arrayOf(
                    amount,
                    PluginLocale.currencyName ?: "EVER"
                )
            )
            return false
        }

        //  Check that private key exists and accessible
        val encryptedPrivateKey = PluginSecureStorage.getPrivateKey(from.uniqueId.toString())
            ?: run {
                if (onFail != null) onFail(from, "error.account.not_created", null)
                return false
            }

        onUpdate?.let { update ->
            update(from, PluginLocale.getLocalizedMessage("transfer.status.unpacking_keys", prefix = true))
        }

        val privateKey = try {
            ChaCha20Poly1305().decryptStringWithPassword(encryptedPrivateKey, password)
        } catch (e: Exception) {
            if (onFail != null) onFail(from, "error.args.password.wrong", null)
            return false
        }

        val publicKey = PluginSecureStorage.derivePublicKey(privateKey)

        val message = try {
            PluginLocale.getLocalizedMessage("fun.creatures").split(',').let {
                when (it.size) {
                    0 -> PluginLocale.getLocalizedMessage("transfer.status.preparing", prefix = true)
                    else -> PluginLocale.getLocalizedMessage(
                        "transfer.status.preparing.creatures",
                        arrayOf(it[Random.nextInt(0, it.lastIndex + 1)].trim()),
                        prefix = true
                    )
                }
            }
        } catch (e: Exception) {
            PluginLocale.getLocalizedMessage("transfer.status.preparing", prefix = true)
        }

        onUpdate?.let { update ->
            update(from, message)
        }

        //  Creating transaction on multisig
        val tx = withContext(Dispatchers.Default) {
            val onFailMsig: ((EVER.TransactionFailReason) -> Unit) = { reason ->
                val (errorMessage, args) =
                    when (reason) {
                        EVER.TransactionFailReason.EVER_API_NOT_CONFIGURED -> Pair("error.tonapi.not_configured", null)
                        EVER.TransactionFailReason.OTHER_API_ERROR -> Pair("error.tonapi.other", null)
                        EVER.TransactionFailReason.INSUFFICIENT_BALANCE -> Pair(
                            "error.account.not_deployed",
                            arrayOf<Any>(PluginLocale.currencyName ?: "ÊVER")
                        )
                        EVER.TransactionFailReason.TX_VALUE_EMPTY -> Pair("error.args.amount.not_numeric", null)
                        EVER.TransactionFailReason.TX_VALUE_NOT_POSITIVE -> Pair("error.args.amount.not_positive", null)
                        EVER.TransactionFailReason.TX_ABORTED -> Pair("transfer.status.error", null)
                        EVER.TransactionFailReason.TOO_MANY_UNFINISHED_TXS -> Pair(
                            "error.transfer.tx.too_many_unfinished",
                            null
                        )
                        EVER.TransactionFailReason.OTHER -> Pair("error.transfer.tx.uninit", null)
                    }
                if (onFail != null) onFail(from, errorMessage, args)
            }

            EVER.createTransactionToSignOnMultisig(
                fromAddress = addressFrom,
                toAddress = addressTo,
                value = (amountBD * BigDecimal(1_000_000_000)).setScale(0, RoundingMode.HALF_DOWN).toString(),
                onFail = onFailMsig
            )
        } ?: run {
            if (onFail != null) _onFail()
            return false
        }

        val (msigTxId, txId, _, _) = tx

        //  Check that transaction has created
        if (msigTxId == null) {
            if (onFail != null) onFail(from, "error.transfer.tx.uninit", null)
            return false
        }

        onUpdate?.let { update ->
            update(from, PluginLocale.getLocalizedMessage("transfer.status.signing", prefix = true))
        }

        //  Check that signing has passed successfully
        val withdrawalResult = withContext(Dispatchers.Default) {
            EVER.confirmSafeMultisigTransaction(
                addressFrom,
                msigTxId,
                KeyPair(publicKey, privateKey)
            )
        }

        return if (withdrawalResult) {
            val fromMessage = PluginLocale.getLocalizedMessage(
                "transfer.status.success",
                arrayOf(
                    amount,
                    PluginLocale.currencyName ?: "EVER",
                    if(isStoreTx) {
                        PluginLocale.getLocalizedMessage("store.name", colored = false)
                    } else {
                        to?.name ?: addressTo.mask(isAddress = true)
                    }
                ),
                prefix = true
            )

            val toMessage = PluginLocale.getLocalizedMessage(
                "transfer.incoming",
                arrayOf(
                    from.name,
                    amount,
                    PluginLocale.currencyName.toString()
                ),
                prefix = true
            )

            //  Get transaction hash, if any
            withContext(Dispatchers.Default) {
                EVER.getTransaction(txId)
            }?.let {
                it.transactionHash?.let { hash ->
                    val txHash = PluginLocale.getLocalizedMessage("transfer.tx_hash", prefix = true)
                        .append(Component.text(" ${ChatColor.GREEN}"))
                        .append(Component.text(hash.mask()).apply {
                            this.clickEvent(
                                ClickEvent.clickEvent(
                                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                                    hash
                                )
                            )
                        })
                    fromMessage.append(txHash)
                    toMessage.append(txHash)
                }
            }

            onUpdate?.let { update ->
                //  Default success update
                update(from, fromMessage)
                to?.let {
                    update(it, toMessage)
                }
            }

            onSuccess?.let { success ->
                //  Custom success update
                success(from, null, null)
            }

            true
        } else {
            if (onFail != null) onFail(from, "error.unknown", null)
            false
        }
    }

    private fun String.mask(leave: Int = 6, delimiter: String = "...", isAddress: Boolean = false): String {
        var result: String = this
        var prefix: String = ""

        if (isAddress) {
            this.split(':').let { chunks ->
                if (chunks.size > 1) {
                    prefix = chunks[0] + ":"
                }
            }
            result = this.drop(prefix.length)
        }

        if (result.length <= leave * 2) return (prefix + result)

        return prefix +
                result.substring(0, leave) +
                delimiter +
                result.substring(result.length - leave, result.length)
    }
}
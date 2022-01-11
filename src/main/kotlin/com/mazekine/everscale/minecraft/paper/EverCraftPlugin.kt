package com.mazekine.everscale.minecraft.paper

import com.google.gson.GsonBuilder
import com.mazekine.everscale.EVER
import com.mazekine.everscale.minecraft.paper.Store.isStoreItem
import com.mazekine.libs.PluginLocale
import com.mazekine.libs.PluginSecureStorage
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

class EverCraftPlugin : JavaPlugin(), Listener {
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }
    private val gson by lazy {
        GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .create()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("EverCraft plugin has started")
        }
    }

//    private val gson = Gson()
//    private var board: Scoreboard? = null

    override fun onEnable() {
        //  Set default configurations
        this.saveDefaultConfig()

        val storagePassword = config.getString("storage.password")
        if (storagePassword == null || storagePassword == "") {
            config.addDefault("storage.password", PluginSecureStorage.generatePrivateKey())
        }

        val cipherSalt = config.getString("security.salt")
        if (cipherSalt == null || cipherSalt == "") {
            config.addDefault("security.salt", PluginSecureStorage.generatePrivateKey())
        }

        val locale = config.getString("locale")
        if(!(locale == null || locale == "")) {
            PluginLocale.setLocale(locale)
        }

        config.options().copyDefaults(true)
        saveConfig()

        //  Set API access data
        EVER.loadConfiguration(config)

        //  Register the glow effect for the store item
        registerGlowEffect()

        //  Load store configuration
        Store.wakeUp()

        //  Commands
        getCommand("e_send")?.setExecutor(ESendCommand())
        getCommand("e_pk")?.setExecutor(EPKCommand())
        getCommand("e_register")?.setExecutor(ERegisterCommand())
        getCommand("e_new_password")?.setExecutor(ENewPasswordCommand())
        getCommand("e_balance")?.setExecutor(EBalanceCommand())
        getCommand("e_withdraw")?.setExecutor(EWithdrawCommand())
        getCommand("e_address")?.setExecutor(EAddressCommand())
        getCommand("e_userdata")?.setExecutor(EUserDataCommand())
        getCommand("e_version")?.setExecutor(EVersionCommand())
        getCommand("e_store")?.setExecutor(EStoreCommand())
        getCommand("e_coupon")?.setExecutor(ECouponCommand())

        //  Commands suggestions
        getCommand("e_send")?.tabCompleter = ESendCommandSuggestion()
        getCommand("e_pk")?.tabCompleter = EPKCommandSuggestion()
        getCommand("e_register")?.tabCompleter = ERegisterCommandSuggestion()
        getCommand("e_new_password")?.tabCompleter = ENewPasswordCommandSuggestion()
        getCommand("e_withdraw")?.tabCompleter = EWithdrawCommandSuggestion()
        getCommand("e_address")?.tabCompleter = EAddressCommandSuggestion()
        getCommand("e_coupon")?.tabCompleter = ECouponCommandSuggestion()

        server.pluginManager.registerEvents(this, this)

        //  Inject logging filter
        PasswordFilter().registerFilter()
    }

    override fun onDisable() {
        PluginSecureStorage.unload()
        super.onDisable()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerId = player.uniqueId.toString()
        val firstNotice = PluginSecureStorage.getPlayerFirstNotice(playerId) ?: false

        if (!firstNotice) {
            player.sendMessage(
                PluginLocale.prefixRegular +
                PluginLocale.getLocalizedMessage(
                    "greeting",
                    arrayOf(
                        player.name,
                        PluginLocale.currencyName ?: "EVER"
                    )
                )
            )

            PluginSecureStorage.setPlayerFirstNotice(playerId, true)
        }

        Store.givePlayerStoreItem(player)

        //  Set scoreboard
/*
        val objective = board?.registerNewObjective("Stats", "dummy", net.kyori.adventure.text.Component.text("${ChatColor.GREEN}Stats")) ?: return
        objective.displaySlot = DisplaySlot.SIDEBAR
        val everBalance = objective.getScore("${ChatColor.GOLD}Balance, EVER:")
        val balance = runBlocking {
            PluginSecureStorage.findAddressByPlayerId(player.uniqueId.toString())?.let {
                EVER.getAddress(it)?.balance?.toBigDecimalOrNull() ?: BigDecimal(0.0)
            } ?: BigDecimal(0.0)
        }

        everBalance.score = balance.intValueExact()
*/
    }

    /**
     * Recipe from https://www.spigotmc.org/threads/how-to-set-item-enchanted-without.339152/
     *
     */
    private fun registerGlowEffect() {
        try {
            val f = Enchantment::class.java.getDeclaredField("acceptingNew")
            f.isAccessible = true
            f.set(null, true)
        } catch (e: Exception) {
            logger.error(
                "Error 0x1 while registering the glow effect\n" +
                e.message + "\n" +
                e.stackTrace.joinToString("\n")
            )
        }

        try {
            val glow = Glow(
                NamespacedKey(this, "glow")
            )
            Enchantment.registerEnchantment(glow)
        } catch (e: Exception) {
            logger.error(
                "Error 0x2 while registering the glow effect\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
        }
    }


}
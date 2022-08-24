package com.mazekine.everscale.minecraft.paper

import com.google.gson.GsonBuilder
import com.mazekine.everscale.EVER
import com.mazekine.everscale.models.APIConfig
import com.mazekine.everscale.models.TonosConfig
import com.mazekine.libs.ChaCha20Poly1305
import com.mazekine.libs.PLUGIN_NAME
import com.mazekine.libs.bStats.Metrics
import com.mazekine.libs.PluginLocale
import com.mazekine.libs.PluginSecureStorage
import com.mazekine.libs.bStats.CustomMetrics
import com.mazekine.libs.migration.Migration
import com.mazekine.libs.migration.Migration_0_2_3
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import kotlin.jvm.Throws

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
            println("$PLUGIN_NAME plugin has started")
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
        if (!(locale == null || locale == "")) {
            PluginLocale.setLocale(locale)
        }

        config.options().copyDefaults(true)
        saveConfig()

        //  Prepare and perform migrations
        doMigrations()

        //  Init cryptography
        config.get("security.salt")?.let {
            ChaCha20Poly1305.Companion.salt = it as String
        }
        config.get("storage.password")?.let {
            PluginSecureStorage.storagePassword = it as String
        }

        //  Init secure storage
        PluginSecureStorage.init()

        //  Set API access data
        val everAPIConfig = APIConfig(
            config.getString("api.endpoint", "https://ton-api.broxus.com")
                ?: System.getenv("EVERCRAFT_API_ENDPOINT"),
            config.getString("api.prefix", "/ton/v3")
                ?: System.getenv("EVERCRAFT_API_PREFIX"),
            config.getString("api.key", null)
                ?: System.getenv("EVERCRAFT_API_KEY"),
            config.getString("api.secret", null)
                ?: System.getenv("EVERCRAFT_API_SECRET")
        )

        val tonosConfig = TonosConfig(
            config.getString("tonos.network", "main.ton.dev")
                ?: System.getenv("EVERCRAFT_TONOS_NETWORK"),
            config.getStringList("tonos.endpoints")
        )

        EVER.loadConfiguration(everAPIConfig, tonosConfig)

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
        getCommand("e_upgrade")?.setExecutor(EUpgradeCommand())

        //  Commands suggestions
        getCommand("e_send")?.tabCompleter = ESendCommandSuggestion()
        getCommand("e_pk")?.tabCompleter = EPKCommandSuggestion()
        getCommand("e_register")?.tabCompleter = ERegisterCommandSuggestion()
        getCommand("e_new_password")?.tabCompleter = ENewPasswordCommandSuggestion()
        getCommand("e_withdraw")?.tabCompleter = EWithdrawCommandSuggestion()
        getCommand("e_address")?.tabCompleter = EAddressCommandSuggestion()
        getCommand("e_coupon")?.tabCompleter = ECouponCommandSuggestion()
        getCommand("e_upgrade")?.tabCompleter = EPKCommandSuggestion()

        server.pluginManager.registerEvents(this, this)

        //  Inject logging filter
        PasswordFilter().registerFilter()

        //  Add bStats
        val pluginId = 13970
        val metrics = Metrics(this, pluginId)
        metrics.addCustomChart(CustomMetrics.couponPrice())
        metrics.addCustomChart(CustomMetrics.storeItems())
        metrics.addCustomChart(CustomMetrics.storeAverageLotSize())
        metrics.addCustomChart(CustomMetrics.storeAverageLotPrice())
        metrics.addCustomChart(CustomMetrics.everCraftAccounts())
        metrics.addCustomChart(CustomMetrics.everAccounts())
    }

    override fun onDisable() {
        PluginSecureStorage.unload()
        super.onDisable()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerId = player.uniqueId.toString()
        val welcomeNotification = PluginSecureStorage.getPlayerWelcomeNotification(playerId) ?: false

        if (!welcomeNotification) {
            player.sendMessage(
                PluginLocale.prefixRegular +
                        PluginLocale.getLocalizedMessage(
                            "notifications.greeting",
                            arrayOf(
                                player.name,
                                PluginLocale.currencyName ?: "EVER"
                            )
                        )
            )

            PluginSecureStorage.setPlayerWelcomeNotification(playerId, true)
        }

        val walletsUpgradeNotification = PluginSecureStorage.getWalletUpgradeRequiredNotification(playerId) ?: false

        if (!walletsUpgradeNotification) {
            val wallets = PluginSecureStorage.walletsToUpgrade(playerId)
            if (!wallets.isEmpty()) {
                player.sendMessage(
                    PluginLocale.prefixRegular +
                    PluginLocale.getLocalizedMessage("notifications.wallets.upgrade.required")
                )

                PluginSecureStorage.setWalletUpgradeRequiredNotification(playerId, true)
            }
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
            logger.warn(
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
            logger.warn(
                "Error 0x2 while registering the glow effect\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
        }
    }

    @Throws(RuntimeException::class)
    private fun doMigrations() {
        Migration_0_2_3()
        Migration.migrateAll()
    }
}
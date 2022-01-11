package com.mazekine.everscale.minecraft.paper

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.mazekine.everscale.EVER
import com.mazekine.everscale.minecraft.paper.providers.FixedBinaryUniqueOrderProvider
import com.mazekine.everscale.minecraft.paper.providers.IUniqueOrderProvider
import com.mazekine.libs.PluginLocale
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.apache.commons.lang.WordUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths

object Store : Listener {
    //  Logger
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }

    //  JSON adapter
    private val gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .setLenient()
            .create()
    }

    //  Configuration
    private val CONFIG_FILE by lazy {
        Files.createDirectories(Paths.get("plugins/EverCraft"))
        File("plugins/EverCraft/store.json")
    }
    private val config by lazy {
        try {
            gson.fromJson(CONFIG_FILE.bufferedReader(), Config::class.java)
        } catch (e: Exception) {
            logger.warn(
                "Error initializing EverCraft Store\n" +
                        e.message + "\n" +
                        e.stackTrace.joinToString("\n")
            )
            null
        }
    }

    //  Custom items
    private val buttonsMapping: Map<Buttons, Pair<Int, Material>> = mapOf(
        Buttons.EMPTY_ITEM to Pair(0xEC000, Material.LIGHT_GRAY_STAINED_GLASS_PANE),
        Buttons.STORE_ITEM to Pair(0xEC001, Material.COMPASS),
        Buttons.BUTTON_BACK to Pair(0xEC002, Material.CROSSBOW),
        Buttons.BUTTON_NEXT to Pair(0xEC003, Material.TRIDENT),
        Buttons.BUTTON_CART to Pair(0xEC004, Material.ENDER_CHEST),
        Buttons.BUTTON_STORE to Pair(0xEC005, Material.GLOW_ITEM_FRAME),
        Buttons.COUPON to Pair(0xEC006, Material.DIAMOND)
    )

    //  The item held by the player that lets to open the store with right click
    private val storeItem: ItemStack

    init {
        val name = Component.text(
            PluginLocale.getLocalizedMessage("store.item.name").trim()
        )
        val lore = PluginLocale
            .getLocalizedMessage(
                "store.item.description",
                arrayOf(PluginLocale.currencyName ?: "EVER")
            ).split("\n")
            .map { Component.text(it.trim()) }

        storeItem = CustomItemBuilder().build(
            buttonsMapping[Buttons.STORE_ITEM]!!.first,
            buttonsMapping[Buttons.STORE_ITEM]!!.second,
            name,
            lore,
            1,
            true
        )
    }

    //  Store buttons
    val backButton: ItemStack by lazy {
        buttonsMapping[Buttons.BUTTON_BACK]!!.let {
            getCustomButton(it.first, "back", it.second, true)
        }
    }
    val nextButton: ItemStack by lazy {
        buttonsMapping[Buttons.BUTTON_NEXT]!!.let {
            getCustomButton(it.first, "next", it.second, true)
        }
    }
    val cartButton: ItemStack by lazy {
        buttonsMapping[Buttons.BUTTON_CART]!!.let {
            getCustomButton(it.first, "cart", it.second, true)
        }
    }
    val storeButton: ItemStack by lazy {
        buttonsMapping[Buttons.BUTTON_STORE]!!.let {
            getCustomButton(it.first, "store", it.second, true)
        }
    }
    val emptySlot: ItemStack by lazy {
        buttonsMapping[Buttons.EMPTY_ITEM]!!.let {
            getCustomButton(it.first, "empty", it.second).apply {
                this.itemMeta.setLocalizedName(null)
                this.itemMeta.lore(null)
            }
        }
    }
    val coupon: ItemStack by lazy {
        buttonsMapping[Buttons.COUPON]!!.let {
            getCustomButton(it.first, "coupon", it.second, true)
        }
    }

    var couponPrice: BigDecimal = BigDecimal(1)
    var storeWallet: String? = null
        private set
    private val storeItems: MutableMap<Material, Item> = mutableMapOf()
    private var isInitialized: Boolean = false

    //  Collection of windows opened by players
    private var storeGUIs: MutableMap<Player, GUI> = mutableMapOf()

    //  Sets how to place items in the storefront
    private var pattern: IUniqueOrderProvider? = null

    /**
     * Creates a store custom button to distinguish from items on sale
     *
     * @param code      Unique item code
     * @param name      Item name
     * @param material  Material
     * @param glow      Indicates if item should have a glow effect
     */
    private fun getCustomButton(code: Int, name: String, material: Material, glow: Boolean = false) =
        CustomItemBuilder().build(
            code,
            material,
            Component.text(
                PluginLocale
                    .getLocalizedMessage("store.buttons.$name.name")
            ),
            PluginLocale
                .getLocalizedMessage("store.buttons.$name.description")
                .split("\n")
                .map { Component.text("${ChatColor.DARK_GRAY}$it") },
            1,
            addGlow = glow
        )

    /**
     * Gives player the store item to easily open the store with the right click
     *
     * @param player
     */
    fun givePlayerStoreItem(player: Player) {
        player.inventory.contents.forEach { slot ->
            @Suppress("UNNECESSARY_SAFE_CALL")  //  In fact slot can be nullable, don't trust Kotlin
            slot?.let {
                if (it.isStoreItem(Buttons.STORE_ITEM)) return
            }
        }

        player.inventory.addItem(storeItem)
    }

    /**
     * Gets the actual store configuration from the settings file and validates it
     *
     */
    fun wakeUp() {
        config?.let {
            storeItems.clear()
            storeWallet = null
            couponPrice = BigDecimal(0)

            if (it.storeWallet == "") {
                logger.error("Store system wallet not configured. Turning off...")
                return
            }

            val storeWalletCorrect = runBlocking { EVER.checkAddress(it.storeWallet) }
            if (storeWalletCorrect != true) {
                logger.error("Store system wallet address invalid. Turning off...")
                return
            }

            if (it.couponPrice < BigDecimal(0)) {
                logger.error("Store coupon price cannot be negative. Found ${it.couponPrice}")
                return
            }

            storeWallet = it.storeWallet
            couponPrice = it.couponPrice

            if (it.items.isEmpty()) {
                logger.info("Store has no contents, nothing to configure")
                return
            }

            it.items.forEach { item ->
                val material = Material.getMaterial(item.type) ?: run {
                    logger.warn("Store item ${item.type} is not recognized")
                    return@forEach
                }

                if (item.price < 0) {
                    logger.warn("Item price cannot be negative. Found ${item.price} for material ${item.type}")
                    return@forEach
                }

                if (item.lotSize < 1) {
                    logger.warn("Lot size must be positive. Found ${item.lotSize} for material ${item.type}")
                    return@forEach
                }

                if (item.lotSize > material.maxStackSize) {
                    logger.warn("Lot size cannot be more than game allows (${material.maxStackSize}). Found ${item.lotSize} for material ${item.type}")
                    return@forEach
                }

                storeItems[material] = item
            }

            it.pattern?.let { pattern ->
                if (File(pattern.path).exists()) {
                    this.pattern = when (pattern.provider) {
                        SupportedUOP.FixedBinary -> FixedBinaryUniqueOrderProvider(File(pattern.path))
                    }
                } else {
                    logger.warn("Pattern file not found. Using default layout...")
                }
            }
        } ?: return
        registerEvents()
        isInitialized = true
        build()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun build() {
        if (!isInitialized) {
            logger.error("EverCraft Store was not initialized properly")
            return
        }

        //  Wipe all opened store windows
        if (storeGUIs.isNotEmpty()) {
            storeGUIs.forEach { (_, gui) ->
                gui.close()
            }

            storeGUIs.clear()
        }
    }

    /**
     * Open inventory for player
     *
     * @param player
     */
    fun open(player: Player) {
        if (storeGUIs.containsKey(player)) close(player)

        //  Init GUI
        val gui = GUI(
            player,
            PluginLocale.getLocalizedMessage("store.name", colored = false),
            DefaultClicker()
        )

        //  Load store items
        storeItems.forEach { item ->
            val itemStack = ItemStack(item.key, item.value.lotSize)
            val meta = itemStack.itemMeta
            val lore: MutableList<Component> = meta.lore() ?: mutableListOf()
            lore.add(
                Component.text("${ChatColor.GREEN} Price: ${item.value.price}")
            )
            meta.lore(lore)
            itemStack.itemMeta = meta
            gui.items.add(itemStack)
        }

        storeGUIs[player] = gui

        storeGUIs[player]?.open() ?: run {
            logger.warn("Store GUI not found")
        }
    }

    /**
     * Closes open inventory for the given player, if open
     *
     * @param player
     */
    fun close(player: Player) {
        storeGUIs[player]?.close()
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player !is Player) return
        storeGUIs[event.player as Player]?.opened = false
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPluginDisable(event: PluginDisableEvent) {
        storeGUIs.forEach { (player, _) ->
            close(player)
            storeGUIs.remove(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerLeave(event: PlayerQuitEvent) {
        storeGUIs.remove(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player
        val gui = storeGUIs[player] ?: return

        val inventoryTitle = try {
            (event.view.title() as TextComponent).content()
        } catch (e: Exception) {
            return
        }

        //  Skip clicks in the lower part of the inventory
        if (event.clickedInventory?.type == InventoryType.PLAYER) return

        //  Skip events from other windows
        if (inventoryTitle != gui.name) return

        event.result = Event.Result.DENY

        //  Avoid stealing all inventory
        if (event.click != ClickType.LEFT) return

        event.currentItem?.let { item ->
            val row = gui.getRowFromSlot(event.slot)
            val rowSlot = event.slot - row.index * 9

            gui.onClick.click(
                player,
                gui,
                row,
                rowSlot,
                item
            )
        }
    }

    @EventHandler
    fun onPlayerInteraction(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        if (!event.action.isRightClick) return
        if (item.isStoreItem(Buttons.STORE_ITEM)) {
            event.isCancelled = true
            open(player)
        }
    }

    private fun registerEvents() {
        Bukkit.getPluginManager().let { pm ->
            pm.getPlugin("EverCraft")?.let { plugin ->
                pm.registerEvents(this, plugin)
            }
        }
    }

    /**
     * Check if item belongs to store buttons collection
     *
     * @return
     */
    fun ItemStack.isStoreItem(): Boolean {
        val buttonMapped = buttonsMapping
            .firstNotNullOfOrNull {
                it.takeIf { it.value.second == this.type }
            } ?: return false
        val materialCode = if (this.itemMeta.hasCustomModelData()) this.itemMeta.customModelData else return false
        return materialCode == buttonMapped.value.first
    }

    /**
     * Check if item is strictly a specific store button
     *
     * @param strict    Button type
     * @return
     */
    fun ItemStack.isStoreItem(strict: Buttons): Boolean {
        val buttonMapped = if (buttonsMapping.containsKey(strict)) buttonsMapping[strict] else return false
        val materialCode = if (this.itemMeta.hasCustomModelData()) this.itemMeta.customModelData else return false
        return materialCode == buttonMapped?.first
    }

    /**
     * Return a store item if the ItemStack belongs to Buttons collection, or null otherwise
     *
     * @return
     */
    fun ItemStack.storeItem(): Buttons? {
        val buttonMapped = buttonsMapping
            .firstNotNullOfOrNull {
                it.takeIf { it.value.second == this.type }
            } ?: return null

        val materialCode = if (this.itemMeta.hasCustomModelData()) this.itemMeta.customModelData else return null

        return if (materialCode == buttonMapped.value.first) buttonMapped.key else null
    }

    /**
     * Count all items of specific type in player's inventory
     *
     * @param filter    Filter to select specific types of items
     * @receiver
     * @return
     */
    fun Inventory.countAll(filter: (predicate: ItemStack?) -> Boolean): Int {
        val subset = this.filter(filter)
        return if (subset.isEmpty()) 0
        else subset.fold(0) { sum, i -> sum + i.amount }
    }

    /**
     * Store interface based on inventory window
     *
     * @property name       Store name (to be displayed in the top area)
     * @property onClick    Callback function reacting to clicking on the store item
     * @constructor Create empty GUI
     */
    class GUI(
        private val owner: Player,
        name: String,
        var onClick: OnClick
    ) : Listener {
        var name: String = name
            set(value) {
                field = value.trim()
            }
        private val rows = 6
        private val totalSlots = 54
        var opened: Boolean = false

        val items: MutableList<ItemStack> = mutableListOf()
        var currentPage: Int = 0
            private set

        init {
            registerEvents()
        }

        /**
         * Register events in Bukkit
         */
        private fun registerEvents() {
            Bukkit.getPluginManager().let { pm ->
                pm.getPlugin("EverCraft")?.let { plugin ->
                    pm.registerEvents(this, plugin)
                }
            }
        }

        /**
         * Open GUI to player
         *
         * @param page  Number of page to open
         * @return  GUI
         */
        fun open(page: Int = currentPage): GUI {
            if (opened) storeGUIs[owner]?.close()

            storeGUIs[owner] = this
            owner.openInventory(getInventory(owner, page))
            opened = true

            return this
        }

        /**
         * Close GUI for player
         *
         * @return  GUI
         */
        fun close(): GUI {
            if (owner.isOnline) {
                if (owner.openInventory.title().equals(name)) {
                    owner.closeInventory()
                }
            }

            opened = false
            return this
        }

        /**
         * Update the title of the store window
         *
         * @param name  New name
         * @return
         */
        fun updateName(name: String): GUI {
            this.name = name
            this.close()
            this.open(currentPage)
            return this
        }

        /**
         * Generate the inventory to display to player
         *
         * @param player    Inventory owner
         * @param page      Page in the storefront
         * @return  Inventory
         */
        private fun getInventory(player: Player, page: Int = currentPage): Inventory {
            //  Create empty inventory
            val inventory = Bukkit.createInventory(player, totalSlots, Component.text(name))

            //  If there are no items in the store, just open an empty inventory
            if (items.isEmpty()) return inventory

            //  If unique order provider is used, number of available places can be less than total
            val uopSlotsPerFullPage = pattern?.places?.let { minOf(it, totalSlots) } ?: totalSlots
            val uopSlotsPerPage = pattern?.let { minOf(it.placesInRows(rows - 2), 9 * (rows - 2)) } ?: (9 * (rows - 2))

            //  Calculate max number of pages and items placed per page
            val (maxPages, maxSlotsPerPage) = when {
                (items.size <= uopSlotsPerFullPage) -> Pair(
                    0,
                    uopSlotsPerFullPage
                )
                else -> Pair(
                    items.size / uopSlotsPerPage,
                    uopSlotsPerPage
                )
            }

            val lastPage = page >= maxPages
            val firstPage = page <= 0

            currentPage = when {
                firstPage -> 0
                lastPage -> maxPages
                else -> page
            }

            val firstIndex = currentPage * maxSlotsPerPage
            val lastIndex = minOf(
                (currentPage + 1) * maxSlotsPerPage - 1,
                items.lastIndex
            )

            //  If the items pattern is loaded, use it to draw the storefront
            pattern?.let {
                //  Reset the pattern cursor
                it.restart()
                for (i in firstIndex..lastIndex) {
                    inventory.setItem(
                        it.next() ?: break, //  In case for some reason the pattern finishes earlier than iterator
                        items[i]
                    )
                }
            } ?: run {  //  Use the default flat layout instead
                for ((j, i) in (firstIndex..lastIndex).withIndex()) {
                    inventory.setItem(j, items[i])
                }
            }

            if (!firstPage) inventory.setItem(
                (rows - 1) * 9,
                backButton
            )

            if (!lastPage) inventory.setItem(
                rows * 9 - 1,
                nextButton
            )

            return inventory
        }

        fun addButton(row: Row, position: Int, item: ItemStack, quantity: Int = 1): GUI {
            items[row.index * 9 + position] = item.asQuantity(
                if (quantity < 1) 1 else if (quantity > item.maxStackSize) item.maxStackSize else quantity
            )
            return this
        }

        fun getRow(index: Int) = Row(index, items)

        fun getRowFromSlot(slot: Int) = Row(slot / 9, items)

        class Row(
            rowIndex: Int,
            items: List<ItemStack>
        ) {
            val index: Int = rowIndex
            var items: MutableList<ItemStack> = mutableListOf()
                private set

            init {
                repeat(9) { this.items.add(Store::emptySlot.get()) }

                for ((j, i) in ((rowIndex * 9)..(rowIndex * 9 + 8)).withIndex()) {
                    if (items.indices.contains(i)) this.items[j] = items[i]
                }
            }
        }

        interface OnClick {
            fun click(player: Player, gui: GUI, row: Row, slot: Int, item: ItemStack): Boolean
        }
    }

    class DefaultClicker : GUI.OnClick {
        @OptIn(DelicateCoroutinesApi::class)
        override fun click(player: Player, gui: GUI, row: GUI.Row, slot: Int, item: ItemStack): Boolean {
            item.storeItem()?.let { button ->
                //  Handle navigation
                when (button) {
                    Buttons.EMPTY_ITEM,
                    Buttons.COUPON,
                    Buttons.STORE_ITEM,
                    Buttons.BUTTON_CART,
                    Buttons.BUTTON_STORE -> return true  //  Do nothing

                    Buttons.BUTTON_BACK -> {
                        gui.open(gui.currentPage - 1)
                        return true
                    }

                    Buttons.BUTTON_NEXT -> {
                        gui.open(gui.currentPage + 1)
                        return true
                    }
                }
            }

            if (item.type == Material.AIR) return true

            //  If player's inventory is full, he cannot buy anything
            if (player.inventory.firstEmpty() == -1) {
                player.sendMessage(
                    PluginLocale.getLocalizedError("store.order.inventory_full", prefix = true)
                )
                return false
            }

            val lotPrice = storeItems[item.type]?.price ?: return false
            val lotSize = storeItems[item.type]?.lotSize ?: return false

            //  Order quantity cannot be less than a lot
            val quantity = maxOf(item.amount, lotSize)

            val lots = quantity.floorDiv(lotSize)
            val orderPrice = lotPrice * lots

            val couponsAvailable = try {
                player
                    .inventory
                    .countAll { it?.isStoreItem(Buttons.COUPON) ?: false }
            } catch (e: Exception) {
                player.sendMessage(
                    PluginLocale.getLocalizedError("store.order.fail.other", prefix = true)
                )
                notify(player, PluginLocale.getLocalizedMessage("store.order.fail.other.short"))
                logger.warn("Failed order\n${e.message}\n${e.stackTraceToString()}")
                return false
            }

            if (couponsAvailable < orderPrice) {
                player.sendMessage(
                    PluginLocale.getLocalizedError("store.order.fail.insufficient_funds", prefix = true)
                )
                notify(player, PluginLocale.getLocalizedMessage("store.order.fail.insufficient_funds.short"))
                return false
            }

            if (charge(player, orderPrice)) {
                if (!deliverOrder(
                        player,
                        ItemStack(item.type, lots * lotSize)
                    )
                ) {
                    player.sendMessage(
                        PluginLocale.getLocalizedError(
                            "store.order.fail.other",
                            prefix = true
                        )
                    )
                    return false
                }
            } else {
                player.sendMessage(
                    PluginLocale.getLocalizedError(
                        "store.order.fail.insufficient_funds",
                        prefix = true
                    )
                )
                return false
            }

            val itemDisplayName = try {
                if(item.itemMeta.hasLocalizedName()) {
                    item.itemMeta.localizedName
                } else {
                    if (item.itemMeta.hasDisplayName()) {
                        (item.itemMeta.displayName() as TextComponent).content()
                    } else {
                        WordUtils.capitalizeFully(
                            item.type.name
                                .lowercase()
                                .replace("_", " ")
                        )
                    }
                }
            } catch (e: Exception) {
                WordUtils.capitalizeFully(
                    item.type.name
                        .lowercase()
                        .replace("_", " ")
                )
            }

            val newName = PluginLocale.getLocalizedMessage(
                "store.order.success.title",
                arrayOf(
                    orderPrice,
                    lots * lotSize,
                    itemDisplayName
                )
            )

            notify(player, newName)

            player.sendMessage(
                PluginLocale.getLocalizedMessage(
                    "store.order.success",
                    arrayOf(
                        lots * lotSize,
                        itemDisplayName,
                        orderPrice
                    ),
                    prefix = true
                )
            )

            return true
        }

        /**
         * Charge the payment from player
         *
         * @param player    Who to charge
         * @param amount    Number of coupons to charge
         * @return  True if the payment was successful, Else otherwise
         */
        private fun charge(player: Player, amount: Int): Boolean {
            val initialBalance = player
                .inventory
                .countAll { it?.isStoreItem(Buttons.COUPON) ?: false }

            if(initialBalance < amount) return false

            try {
                var remainingPayment = amount

                player
                    .inventory
                    .forEach { stack ->
                        if (stack?.isStoreItem(Buttons.COUPON) == true) {
                            when {
                                stack.amount <= remainingPayment -> {
                                    remainingPayment -= stack.amount
                                    stack.amount = 0
                                }
                                stack.amount > remainingPayment -> {
                                    stack.amount -= remainingPayment
                                    remainingPayment = 0
                                }
                                else -> {
                                    logger.error("[charge] Weird behavior: stack amount cannot be null")
                                }
                            }
                        }
                    }

                if (remainingPayment == 0) return true

            } catch (e: Exception) {
                logger.error("Order execution error:\n${e.message}\n${e.stackTraceToString()}")
            }

            val currentBalance = player
                .inventory
                .countAll { it?.isStoreItem(Buttons.COUPON) ?: false }

            refund(player, initialBalance - currentBalance)

            return false
        }

        /**
         * Refund payment
         *
         * @param player    Who to refund
         * @param amount    Amount of coupons to add
         */
        private fun refund(player: Player, amount: Int) {
            player.inventory.addItem(coupon.asQuantity(amount))
        }

        /**
         * Deliver order to player
         *
         * @param player    Who to deliver
         * @param order     Item in the required quantity
         * @return
         */
        private fun deliverOrder(player: Player, order: ItemStack): Boolean {
            val initialQuantity = player.inventory.countAll {
                it?.let { stack ->
                    stack.type == order.type &&
                            !stack.isStoreItem()
                } ?: false
            }

            player.inventory.addItem(order)

            val newQuantity = player.inventory.countAll {
                it?.let { stack ->
                    stack.type == order.type &&
                            !stack.isStoreItem()
                } ?: false
            }

            if (newQuantity == initialQuantity + order.amount) return true

            revertOrder(player, order.asQuantity(newQuantity - initialQuantity))

            return false
        }

        /**
         * Reverts the player's inventory to the previous state in case the order delivery has failed
         *
         * @param player    Who to revert
         * @param order     Amount of items to deduce from player's inventory
         */
        private fun revertOrder(player: Player, order: ItemStack) {
            var remainingAmount = order.amount

            player
                .inventory
                .forEach {
                    it?.let { stack ->
                        if (!stack.isStoreItem(Buttons.COUPON) && stack.type == order.type) {
                            when {
                                stack.amount <= remainingAmount -> {
                                    stack.amount = 0
                                    remainingAmount -= stack.amount
                                }
                                stack.amount > remainingAmount -> {
                                    stack.amount -= remainingAmount
                                    remainingAmount = 0
                                }
                                else -> {
                                    logger.error("[revertOrder] Weird behavior: stack amount cannot be null")
                                }
                            }
                        }
                    }
                }
        }

        /**
         * Outputs notification in the GUI title and returns it back after a certain delay
         *
         * @param player    Player to send notification to
         * @param message   Notification
         * @param interval  Interval between returning to the initial state
         */
        @OptIn(DelicateCoroutinesApi::class)
        private fun notify(player: Player, message: String, interval: Long = 500) {
            var gui = storeGUIs[player] ?: return
            val oldName = gui.name
            gui.updateName(message)

            runBlocking { delay(interval) }

            //  In case user has closed the window already
            gui = storeGUIs[player] ?: return
            //  The player may have clicked already on another item
            if (gui.name == message.trim()) gui.updateName(oldName)
        }
    }

    /**
     * Buttons used in store GUI
     *
     * @constructor Create empty Buttons
     */
    enum class Buttons {
        EMPTY_ITEM,
        STORE_ITEM,
        BUTTON_BACK,
        BUTTON_NEXT,
        BUTTON_CART,
        BUTTON_STORE,
        COUPON
    }

    /**
     * Store item
     *
     * @property type   Material name
     * @property price  Price in number of coupons per lot
     * @property lotSize   Number of items sold
     * @constructor Create empty Item
     */
    data class Item(
        val type: String,
        val price: Int,
        @SerializedName("lot_size")
        val lotSize: Int,
    )

    /**
     * Store configuration type
     *
     * @property couponPrice Floating price of one coupon in EVERs
     * @property items  Collection of store items
     * @constructor Create empty Config
     */
    data class Config(
        @SerializedName("store_wallet")
        val storeWallet: String,
        @SerializedName("coupon_price")
        val couponPrice: BigDecimal,
        val items: List<Item>,
        val pattern: Pattern? = null
    )

    data class Pattern(
        val path: String,
        val provider: SupportedUOP
    )

    enum class SupportedUOP {
        FixedBinary
    }

/*
    //  TODO: Implement categories in future versions
    enum class Categories {
        MATERIALS,
        WEAPONS,
        ARMOR,
        OTHER
    }
*/
}
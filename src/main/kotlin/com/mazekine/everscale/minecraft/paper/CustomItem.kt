package com.mazekine.everscale.minecraft.paper

import net.kyori.adventure.text.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.enchantments.EnchantmentWrapper
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory

class CustomItemBuilder {
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }

    fun build(
        code: Int,
        material: Material,
        name: TextComponent? = null,
        description: List<TextComponent>? = null,
        quantity: Int = 1,
        addGlow: Boolean = false
    ): ItemStack {
        val item = ItemStack(material, quantity)

        val meta = item.itemMeta

        name?.let {
            meta.displayName(it)
        }

        /**
         * Flag to distinguish special items created by EverCraft
         */
        meta.setCustomModelData(code)

        description?.let {
            if(it.isEmpty()) return@let

            meta.lore(it)
            meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ATTRIBUTES
            )

        }

        item.itemMeta = meta

        if(addGlow) {
            try {
                Enchantment.getByKey(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("EverCraft")!!,
                        "glow"
                    )
                )?.let {enchantment ->
                    item.addUnsafeEnchantment(enchantment, 1)
                }
            } catch (e: Exception) {
                logger.error(
                    "Error while setting the glow effect on item\n" +
                            e.message + "\n" +
                            e.stackTrace.joinToString("\n")
                )
            }
        }

        return item
    }
}
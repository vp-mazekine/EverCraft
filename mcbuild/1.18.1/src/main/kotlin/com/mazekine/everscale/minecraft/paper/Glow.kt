package com.mazekine.everscale.minecraft.paper

import io.papermc.paper.enchantments.EnchantmentRarity
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.enchantments.EnchantmentTarget
import org.bukkit.entity.EntityCategory
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

class Glow(key: NamespacedKey) : Enchantment(key) {
    override fun translationKey(): String {
        return "glow"
    }

    override fun getName(): String {
        return "glow"
    }

    override fun getMaxLevel(): Int {
        return 1
    }

    override fun getStartLevel(): Int {
        return 1
    }

    override fun getItemTarget(): EnchantmentTarget {
        return EnchantmentTarget.VANISHABLE
    }

    override fun isTreasure(): Boolean {
        return false
    }

    override fun isCursed(): Boolean {
        return true
    }

    override fun conflictsWith(other: Enchantment): Boolean {
        return false
    }

    override fun canEnchantItem(item: ItemStack): Boolean {
        return true
    }

    override fun displayName(level: Int): Component {
        return Component.text("Item Glow")
    }

    override fun isTradeable(): Boolean {
        return false
    }

    override fun isDiscoverable(): Boolean {
        return false
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.VERY_RARE
    }

    override fun getDamageIncrease(level: Int, entityCategory: EntityCategory): Float {
        return 0.0F
    }

    override fun getActiveSlots(): MutableSet<EquipmentSlot> {
        return mutableSetOf(
            EquipmentSlot.HAND
        )
    }



}
package com.mazekine.everscale.minecraft.paper

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class EverCraftPlugin: JavaPlugin(), Listener {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("EverCraft plugin has started")
        }
    }

    override fun onEnable() {
        config.addDefault("everscale.currencies.default", "EVER")
        config.options().copyDefaults(true)
        saveConfig()

        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        super.onDisable()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        player.sendMessage("Hello, player with id " + player.uniqueId)
    }
}
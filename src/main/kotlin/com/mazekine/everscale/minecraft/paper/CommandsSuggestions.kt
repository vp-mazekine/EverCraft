package com.mazekine.everscale.minecraft.paper

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

class ESendCommandSuggestion : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val suggestion: MutableList<String> = mutableListOf()

        if(sender !is Player) return null

        return when (args.size) {
            1 -> {
                StringUtil.copyPartialMatches(
                    args[0],
                    Bukkit.getOnlinePlayers().map { it.name },
                    suggestion
                )
                suggestion
            }
            2 -> {
                suggestion.add("Amount to send")
                suggestion
            }
            3 -> {
                suggestion.add("Your password")
                suggestion
            }
            else -> null
        }
    }
}

class EPKCommandSuggestion : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val pwdSuggestion = mutableListOf("${ChatColor.GRAY}Input your wallet password")
        val unknownSuggestion = mutableListOf("${ChatColor.RED}Unknown argument")

        return when(args.size) {
            0 -> null
            1 -> pwdSuggestion
            else -> unknownSuggestion
        }
    }

}

class ERegisterCommandSuggestion : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val pwdSuggestion = mutableListOf("${ChatColor.GRAY}Input your wallet password")
        val repeatSuggestion = mutableListOf("${ChatColor.GRAY}Repeat password")
        val unknownSuggestion = mutableListOf("${ChatColor.RED}Unknown argument")

        return when(args.size) {
            0 -> null
            1 -> pwdSuggestion
            2 -> repeatSuggestion
            else -> unknownSuggestion
        }

    }
}

class ENewPasswordCommandSuggestion : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val oldPwdSuggestion = mutableListOf("${ChatColor.GRAY}Current wallet password")
        val newPwdSuggestion = mutableListOf("${ChatColor.GRAY}New wallet password")
        val repeatSuggestion = mutableListOf("${ChatColor.GRAY}Repeat new password")
        val unknownSuggestion = mutableListOf("${ChatColor.RED}Unknown argument")

        return when(args.size) {
            0 -> null
            1 -> oldPwdSuggestion
            2 -> newPwdSuggestion
            3 -> repeatSuggestion
            else -> unknownSuggestion
        }

    }
}

class EAddressCommandSuggestion : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val pwdSuggestion = mutableListOf("${ChatColor.GRAY}Wallet password (opt.)")
        val unknownSuggestion = mutableListOf("${ChatColor.RED}Unknown argument")

        return when(args.size) {
            0 -> null
            1 -> pwdSuggestion
            else -> unknownSuggestion
        }
    }

}

class EWithdrawCommandSuggestion : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val suggestion: MutableList<String> = mutableListOf()

        if(sender !is Player) return null

        return when (args.size) {
            1 -> {
                suggestion.add("Recipient address")
                suggestion
            }
            2 -> {
                suggestion.add("Amount to send")
                suggestion
            }
            3 -> {
                suggestion.add("Your password")
                suggestion
            }
            else -> null
        }
    }
}

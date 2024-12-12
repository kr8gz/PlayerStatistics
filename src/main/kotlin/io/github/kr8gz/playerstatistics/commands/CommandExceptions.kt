package io.github.kr8gz.playerstatistics.commands

import com.mojang.brigadier.exceptions.CommandExceptionType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.text.Text

enum class CommandExceptions(private val translationKey: String) : CommandExceptionType {
    NO_DATA("playerstatistics.no_data"),
    UNKNOWN_PLAYER("playerstatistics.argument.player.unknown"),

    // Database
    DATABASE_INITIALIZING("playerstatistics.database.initializing"),
    ALREADY_RUNNING("playerstatistics.database.running"),

    // Sharing
    SHARE_UNAVAILABLE("playerstatistics.command.share.unavailable"),
    ALREADY_SHARED("playerstatistics.command.share.already_shared");

    fun getMessage(vararg args: Any?): Text = Text.translatable(translationKey, *args)
    fun create(vararg args: Any?) = CommandSyntaxException(this, getMessage(*args))
}

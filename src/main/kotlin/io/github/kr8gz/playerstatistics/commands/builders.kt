package io.github.kr8gz.playerstatistics.commands

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.util.Identifier
import net.silkmc.silk.commands.ArgumentCommandBuilder
import net.silkmc.silk.commands.CommandBuilder

inline fun <S, B> CommandBuilder<S, B, *>.executes(crossinline command: CommandContext<S>.() -> Unit)
where S : CommandSource,
      B : ArgumentBuilder<S, B> {
    brigadier {
        executes { it.command(); 0 }
    }
}

inline fun <S : CommandSource> ArgumentCommandBuilder<S, *>.suggests(crossinline values: CommandContext<S>.() -> Iterable<String>) {
    brigadier {
        suggests { context, builder ->
            CommandSource.suggestMatching(values(context), builder)
        }
    }
}

inline fun <S : CommandSource> ArgumentCommandBuilder<S, *>.suggestsIdentifiers(crossinline values: CommandContext<S>.() -> Iterable<Identifier>) {
    brigadier {
        suggests { context, builder ->
            CommandSource.suggestIdentifiers(values(context), builder)
        }
    }
}

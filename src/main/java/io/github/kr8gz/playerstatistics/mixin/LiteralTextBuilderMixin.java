package io.github.kr8gz.playerstatistics.mixin;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.silkmc.silk.core.text.LiteralTextBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LiteralTextBuilder.class)
public abstract class LiteralTextBuilderMixin {
    @Redirect(method = "build",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/text/MutableText;setStyle(Lnet/minecraft/text/Style;)Lnet/minecraft/text/MutableText;"))
    private MutableText fixStyle(MutableText instance, Style currentStyle) {
        return instance.styled(style -> style.withParent(currentStyle));
    }
}

package io.github.kr8gz.playerstatistics.mixin;

//? if silk: <1.10.4 {
/*import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?}

import net.silkmc.silk.core.text.LiteralTextBuilder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LiteralTextBuilder.class)
public abstract class LiteralTextBuilderMixin {
    //? if silk: <1.10.4 {
    /*@Redirect(method = "build",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/text/MutableText;setStyle(Lnet/minecraft/text/Style;)Lnet/minecraft/text/MutableText;"))
    private MutableText fixStyle(MutableText text, Style currentStyle) {
        return text.styled(style -> style.withParent(currentStyle));
    }
    *///?}
}

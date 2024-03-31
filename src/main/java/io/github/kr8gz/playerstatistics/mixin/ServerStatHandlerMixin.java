package io.github.kr8gz.playerstatistics.mixin;

import io.github.kr8gz.playerstatistics.database.Database;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.StatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatHandler.class)
public abstract class ServerStatHandlerMixin extends StatHandler {
    @Inject(method = "save()V", at = @At("HEAD"))
    private void save(CallbackInfo ci) {
        Database.launchUpdatePlayerStatistics((ServerStatHandler) (Object) this);
    }
}

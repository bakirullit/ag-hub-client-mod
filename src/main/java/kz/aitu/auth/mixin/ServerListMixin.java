package kz.aitu.auth.mixin;

import kz.aitu.auth.server.AituServerManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into Minecraft's ServerList to:
 * 1. Automatically inject the AITU official server at Index 0.
 * 2. Prevent removing or moving the pinned server.
 * 3. Dynamically update its IP and name from AituApiClient.
 */
@Mixin(ServerList.class)
public abstract class ServerListMixin {

    @Shadow
    @Final
    private List<ServerData> serverList;

    @Inject(method = "load", at = @At("RETURN"))
    private void aitu$pinServerOnLoad(CallbackInfo ci) {
        AituServerManager.ensurePinnedServer(this.serverList);
        AituServerManager.updateServerInfoAsync(() -> {
            // Re-ensure with dynamic info if updated
            AituServerManager.ensurePinnedServer(this.serverList);
        });
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void aitu$ensurePinnedOnSave(CallbackInfo ci) {
        AituServerManager.ensurePinnedServer(this.serverList);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void aitu$preventRemovePinnedServer(ServerData serverData, CallbackInfo ci) {
        if (AituServerManager.isAituServer(serverData)) {
            // Cancel removal to keep the pinned official server always present
            ci.cancel();
        }
    }

    @Inject(method = "swap", at = @At("HEAD"), cancellable = true)
    private void aitu$preventSwapPinnedServer(int i, int j, CallbackInfo ci) {
        // Prevent moving the pinned server from index 0 or moving another server above index 0
        if (i == 0 || j == 0) {
            ci.cancel();
        }
    }
}

package com.jason.plugins.antibadwords.fabric;

import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.mod.AbwCommand;
import com.jason.plugins.antibadwords.mod.ModRuntime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AntiBadWordsFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AntiBadWords");

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer("antibadwords")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1");
        ModRuntime.init(FabricLoader.getInstance().getConfigDir().resolve("antibadwords"), version, "Fabric", new FabricPermissions(),
            new EngineLog() {
                @Override
                public void info(String message) {
                    LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    LOGGER.warn(message);
                }
            });

        // Returning false from ALLOW_CHAT_MESSAGE cancels the message.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !ModRuntime.blockChat(sender, message.signedContent()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ModRuntime.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ModRuntime.onQuit(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(ModRuntime::restoreEverything);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ModRuntime.remember(server);
            ModRuntime.selfTestIfRequested();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AbwCommand.register(dispatcher));
    }
}

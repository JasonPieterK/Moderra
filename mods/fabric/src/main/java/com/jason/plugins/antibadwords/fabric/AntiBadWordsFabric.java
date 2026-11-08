package com.jason.plugins.antibadwords.fabric;

import com.jason.plugins.antibadwords.mod.AbwCommand;
import com.jason.plugins.antibadwords.mod.ModActions;
import com.jason.plugins.antibadwords.mod.ModCore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AntiBadWordsFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AntiBadWords");
    private ModCore core;

    @Override
    public void onInitialize() {
        core = new ModCore(FabricLoader.getInstance().getConfigDir(), LOGGER::info);

        // Returning false from ALLOW_* cancels the message.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
            !core.config().scan.chat || allow(sender, message.signedContent(), "chat"));
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayer player = source.getPlayer();
            return player == null || !core.config().scan.commands || allow(player, message.signedContent(), "command");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> core.onQuit(handler.getPlayer().getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AbwCommand.register(dispatcher, core));
        LOGGER.info("AntiBadWords loaded.");
    }

    private boolean allow(ServerPlayer player, String text, String source) {
        ModCore.Verdict verdict = core.check(player.getUUID(), text);
        if (verdict == null) {
            return true;
        }
        ModActions.handle(player.level().getServer(), player, core, verdict, source);
        return false;
    }
}

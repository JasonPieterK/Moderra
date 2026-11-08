package com.jason.plugins.antibadwords.forge;

import com.jason.plugins.antibadwords.mod.AbwCommand;
import com.jason.plugins.antibadwords.mod.ModActions;
import com.jason.plugins.antibadwords.mod.ModCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("antibadwords")
public final class AntiBadWordsForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AntiBadWords");
    private final ModCore core = new ModCore(FMLPaths.CONFIGDIR.get(), LOGGER::info);

    public AntiBadWordsForge() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("AntiBadWords loaded.");
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!core.config().scan.chat) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        ModCore.Verdict verdict = core.check(player.getUUID(), event.getRawText());
        if (verdict != null) {
            event.setCanceled(true);
            ModActions.handle(player.level().getServer(), player, core, verdict, "chat");
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        core.onQuit(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        AbwCommand.register(event.getDispatcher(), core);
    }
}

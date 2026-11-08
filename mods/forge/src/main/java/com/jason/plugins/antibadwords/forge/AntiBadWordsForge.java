package com.jason.plugins.antibadwords.forge;

import com.jason.plugins.antibadwords.mod.AbwCommand;
import com.jason.plugins.antibadwords.mod.ModCore;
import com.jason.plugins.antibadwords.mod.ModRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("antibadwords")
public final class AntiBadWordsForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AntiBadWords");

    public AntiBadWordsForge() {
        String version = ModList.get().getModContainerById("antibadwords")
            .map(c -> c.getModInfo().getVersion().toString()).orElse("1");
        ModRuntime.init(FMLPaths.CONFIGDIR.get().resolve("antibadwords"), version, new ForgePermissions(),
            new ModCore.Log() {
                @Override
                public void info(String message) {
                    LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    LOGGER.warn(message);
                }
            });
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ForgePermissions.Nodes());
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (ModRuntime.blockChat(event.getPlayer(), event.getRawText())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModRuntime.restoreLeftovers(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModRuntime.onQuit(player);
        }
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent event) {
        ModRuntime.restoreEverything(event.getServer());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        AbwCommand.register(event.getDispatcher());
    }
}

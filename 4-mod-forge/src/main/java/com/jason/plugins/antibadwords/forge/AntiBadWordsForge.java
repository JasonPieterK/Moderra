package com.jason.plugins.antibadwords.forge;

import com.jason.plugins.antibadwords.engine.EngineLog;
import com.jason.plugins.antibadwords.mod.AbwCommand;
import com.jason.plugins.antibadwords.mod.ModRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("antibadwords")
public final class AntiBadWordsForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AntiBadWords");

    public AntiBadWordsForge(FMLJavaModLoadingContext context) {
        String version = readVersion();
        ModRuntime.init(FMLPaths.CONFIGDIR.get().resolve("antibadwords"), version, "Forge", new ForgePermissions(),
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

        ForgePermissions.register();
        // A Predicate listener cancels the event when it returns true.
        ServerChatEvent.BUS.addListener((ServerChatEvent event) -> ModRuntime.blockChat(event.getPlayer(), event.getRawText()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModRuntime.restoreLeftovers(player);
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModRuntime.onQuit(player);
            }
        });
        ServerStoppingEvent.BUS.addListener((ServerStoppingEvent event) -> ModRuntime.restoreEverything(event.getServer()));
        RegisterCommandsEvent.BUS.addListener((RegisterCommandsEvent event) -> AbwCommand.register(event.getDispatcher()));
        net.minecraftforge.event.server.ServerStartedEvent.BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent event) -> ModRuntime.selfTestIfRequested());
    }

    /** The build writes the mod version into antibadwords.version. */
    private static String readVersion() {
        try (var in = AntiBadWordsForge.class.getResourceAsStream("/antibadwords.version")) {
            return in == null ? "1" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException ex) {
            return "1";
        }
    }
}

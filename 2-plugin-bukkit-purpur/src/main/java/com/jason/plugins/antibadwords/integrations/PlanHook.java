package com.jason.plugins.antibadwords.integrations;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.annotation.StringProvider;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.EngineLog;
import java.util.UUID;

/**
 * Plan data extension: violations, strikes and mute state per player. Compiled against a name-only shim of the Plan extension API
 * ({@code src/plan-shim}, not in the jar) and loaded only when Plan is installed. The values are the same ones PlaceholderAPI shows
 * ({@code engine.placeholders()}); no chat text is exposed. Unverified (not visible in the javap dump): that Plan passes a
 * {@code UUID playerUUID} parameter to annotated provider methods (Plan's documented convention).
 */
@PluginInfo(name = "AntiBadWords")
public final class PlanHook implements DataExtension {
    private final Engine engine;

    private PlanHook(Engine engine) {
        this.engine = engine;
    }

    /** Registers now and again whenever Plan reports it was (re-)enabled. */
    static void start(Engine engine, EngineLog log) {
        PlanHook extension = new PlanHook(engine);
        Runnable register = () -> {
            try {
                ExtensionService.getInstance().register(extension);
                log.info("Plan hook: the AntiBadWords data extension is registered (violations, strikes, muted).");
            } catch (Throwable ex) {
                log.warn("Plan hook: registering the data extension failed (" + ex.getClass().getSimpleName() + ").");
            }
        };
        try {
            CapabilityService.getInstance().registerEnableListener(enabled -> {
                if (Boolean.TRUE.equals(enabled)) {
                    register.run();
                }
            });
        } catch (Throwable ex) {
            log.warn("Plan hook: could not wait for Plan to be enabled (" + ex.getClass().getSimpleName() + ").");
        }
        register.run();
    }

    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[] {CallEvents.PLAYER_JOIN, CallEvents.PLAYER_LEAVE};
    }

    @NumberProvider(text = "Violations", description = "Recorded filter violations", priority = 100)
    public long violations(UUID playerUUID) {
        return number(playerUUID, "violations_total");
    }

    @NumberProvider(text = "Strikes", description = "Current strikes", priority = 90)
    public long strikes(UUID playerUUID) {
        return number(playerUUID, "strikes");
    }

    @StringProvider(text = "Muted", description = "Muted by AntiBadWords", priority = 80)
    public String muted(UUID playerUUID) {
        return "true".equals(engine.placeholders().resolve(playerUUID, "muted")) ? "Yes" : "No";
    }

    private long number(UUID id, String key) {
        try {
            return Long.parseLong(engine.placeholders().resolve(id, key));
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}

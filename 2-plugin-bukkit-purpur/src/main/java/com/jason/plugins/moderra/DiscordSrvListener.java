package com.jason.plugins.moderra;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent;

/**
 * The one object handed to {@code DiscordSRV.api.subscribe(...)}. DiscordSRV finds listener methods by its own
 * {@code @Subscribe} annotation, which cannot be put on a method by reflection, so this tiny class is compiled against
 * two name-only shims ({@code src/discordsrv-shim}, not packaged). It is only ever loaded by {@link DiscordSrvHook} after
 * DiscordSRV was found, so the plugin loads fine without DiscordSRV.
 */
public final class DiscordSrvListener {
    private final DiscordSrvHook hook;

    DiscordSrvListener(DiscordSrvHook hook) {
        this.hook = hook;
    }

    /** DiscordSRV calls this for every message from a Discord server channel, before it processes it (the event can be cancelled). */
    @Subscribe
    public void onMessage(DiscordGuildMessagePreProcessEvent event) {
        hook.onMessage(event);
    }
}

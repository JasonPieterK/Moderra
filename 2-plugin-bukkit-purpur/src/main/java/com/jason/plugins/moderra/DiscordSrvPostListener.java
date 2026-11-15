package com.jason.plugins.moderra;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent;

/**
 * Second listener object, subscribed only when {@code DiscordGuildMessagePostProcessEvent} exists with a processed-text setter
 * (see {@link DiscordSrvHook}); kept apart from {@link DiscordSrvListener} so a DiscordSRV without that class still gets the filter.
 */
public final class DiscordSrvPostListener {
    private final DiscordSrvHook hook;

    DiscordSrvPostListener(DiscordSrvHook hook) {
        this.hook = hook;
    }

    /** DiscordSRV calls this after it turned a Discord message into the text for Minecraft, before it is broadcast. */
    @Subscribe
    public void onPostProcess(DiscordGuildMessagePostProcessEvent event) {
        hook.onPostProcess(event);
    }
}

# Task 39: real chat-channel / party / town events (plugin-api-dump run 43667029230)

Done: TownyChat `AsyncChatHookEvent` (reflection, `ChannelChatEvents`, registered only when TownyChat present, switch `integrations.chatchannels.channel-events`).
Channel name -> `Message.withChannel`; blocked -> setCancelled(true), censored -> setMessage. Manifest entries added. mvn test + stub-compile plugin pass.

Per plugin:
- VentureChat: event class found (VentureChatEvent) but NOT cancellable and has no setter (post-format notification) -> not usable. Its chat still passes AsyncPlayerChatEvent check.
- HuskChat: ChatMessageEvent is a proxy-side interface (setMessage/setChannelId, no cancel) -> not usable on a backend.
- ChatControl, CarbonChat, FactionsUUID: jar missing in dump -> not available.
- mcMMO: McMMOPartyChatEvent is cancellable with setMessage, but the player comes from `chat.author.Author` (not in dump). Classes added to tools/plugin-dump-classes.txt; hook left off until next dump.
- TownyChat: real event used.
- TAB / NametagEdit: API only has setters (setPrefix/setSuffix/setName/setNametag) and TAB events only PlayerLoadEvent; no event for player-set text -> command scan kept.

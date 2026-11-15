# Task 45 (G + H): chat-channel events

## Done
- mcMMO party chat: `ChannelChatEvents.registerMcmmoParty` listens (HIGHEST, reflection, only when mcMMO is present) to
  `com.gmail.nossr50.events.chat.McMMOPartyChatEvent` using the dump-verified `McMMOChatEvent` methods getMessage, setMessage(String),
  setCancelled(boolean), isCancelled, getAuthor. Channel name for the engine: `mcmmo-party` (use it in `channel-profiles`).
  Blocked/captured -> cancelled; censored -> setMessage. Switch: `integrations.chatchannels.channel-events` (existing, default true).
- Sender lookup: Author is an adventure Identity (shaded). `uuid()` is NOT in the dump; it is looked up at run time, with a fallback to
  `getAuthoredName(null)` and `Bukkit.getPlayerExact`. If both fail the line is not checked and one warning is logged.
  Added `Identity` to `tools/plugin-dump-classes.txt` so the next dump can confirm `uuid()`.
- Manifest section added; `tools/stub-compile.sh plugin` PASS; engine setting text updated.

## CarbonChat, FactionsUUID, ChatControl (no jar, no class names in the dump; no hook added, nothing guessed)
- CarbonChat: own event, class unknown. Covered only if Carbon fires AsyncPlayerChatEvent; our check runs at LOWEST and `loadbefore`
  lists CarbonChat. Admin test: send a blocked word in a Carbon channel; if it appears, set chat-priority to lowest and report the event class.
- FactionsUUID: faction/ally chat goes through the Bukkit chat event (our LOWEST check). Admin test: blocked word in faction chat.
- ChatControl (paid): verified by design: LOWEST priority plus `loadbefore: ChatControl`. Admin test: blocked word in a ChatControl channel.
- `FactionPlayerChatEvent`, `FactionChatEvent`, `CarbonChatEvent` stay in the dump list; jars could not be obtained.

## Changelog line
- mcMMO party chat is now checked (channel `mcmmo-party`) through McMMOPartyChatEvent.

## Open items
- `Identity.uuid()` on the mcMMO Author is unverified (fallback exists). A real server test with mcMMO party chat is needed.

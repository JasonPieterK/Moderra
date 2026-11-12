# Session 5/5 (hard): Discord integration (part A) and the in-game settings GUI (part B)

## Summary
The last and biggest session. **Part A (feature 8)**: Discord alerts and logging by plain webhook on all four server types, and on Bukkit/Spigot/Paper/Purpur also a
**DiscordSRV** hook (alerts through DiscordSRV channels, filtering of messages bridged from Discord into Minecraft, and moderation commands from Discord).
**Part B**: running `/abw` as a player opens a **chest-style settings menu** that can configure (almost) everything, with sign (or chat) text input and a Done button.
Both parts are subscribers/consumers of the contracts from sessions 2-4: `ModerationEvent`s and the `SettingsRegistry`. **This session is oversized. If you prefer two
sessions, split exactly between Part A and Part B: each part has its own tasks, acceptance criteria and branch.** Prerequisites: sessions 2 and 4 merged (3 strongly
preferred, so the filters appear in the GUI). Read `handoffs/00-shared-context.md` first.

## Tasks - Part A: Discord (`discord.yml`)
A1. **Webhook alerts on all four server types** (engine, `java.net.http`, no libraries). Settings: `enabled`, one or two webhook URLs (alerts channel, optional separate log
    channel for "log everything"), which event kinds go where (`violation`, `punishment`, `mute`, `unmute`, `spam`, `link`, `evasion`, `admin-command`), message style (embed
    or plain, colours per kind, templates with placeholders such as `{player} {word} {source} {world} {count} {limit} {action}`), optional role/user mention for severe events,
    rate limit and batching (respect Discord's limits, handle HTTP 429 `Retry-After`), a bounded async queue that never blocks the server thread and drops oldest with a warning
    when full, timeouts and retries with backoff, a `/abw discord test` command. **Webhook URLs are secrets**: type SECRET in the registry, masked in every log, command output,
    GUI, history and error message; allow `${ENV:NAME}` indirection; warn if the file is world-readable.
A2. **DiscordSRV hook (Bukkit family only)**, soft-depend. Detect DiscordSRV at runtime (reflection or a compile-only dependency if its repository is reachable; the jar must load
    without DiscordSRV installed). Modes in `discord.yml`: `mode: auto|webhook|discordsrv|off`. Via DiscordSRV: (a) send alerts to a configured DiscordSRV channel name; (b) **filter
    Discord to Minecraft**: check messages arriving from Discord with the same engine (source `discord-in`), actions `block`, `censor`, `log-only`, optionally notify the sender in
    Discord, per-channel on/off, bypass via a Discord role id list; (c) **moderate from Discord** through DiscordSRV slash commands (`mute`, `unmute`, `history`, `strikes`, `check`,
    `reload` are separate switches), **default off and fail-closed**: only listed Discord role ids/user ids may run them, every use is audited (`admin-command` event with the
    Discord user), Minecraft names resolved safely, rate limited. Read the installed DiscordSRV version's real API; if the slash-command API is not available in that version,
    disable (c) with a clear log line.
A3. **Fabric/Forge**: A1 only. Document clearly in the docs and in `/abw info` that DiscordSRV-based features are Bukkit-family only; do not simulate them.
A4. **Tests**: unit tests with a local fake HTTP server (payloads, queue, 429 handling, masking of secrets, drop-oldest, shutdown flush), a fake DiscordSRV test double for the hook logic,
    docs `6-guides/DISCORD.md` (ELI5 setup: create a webhook, paste the URL or env var, test).

## Tasks - Part B: in-game GUI
B1. **Engine menu model** (`engine/gui/**`, pure Java, no Minecraft imports): `Menu`, `Page`, `Entry`, `MenuAction`, a navigation stack, pagination (45 content slots + a bottom nav row),
    generated from the `SettingsRegistry`: one **page per category**, entries per setting: toggle (BOOLEAN), number with step buttons (left/right click = +/- step, shift = x10) for INT/DOUBLE/DURATION,
    cycle for ENUM, text input for STRING and SECRET (masked), a list sub-menu for STRING_LIST (paginated view, add by typing, remove by click), "reset to default". Special pages: **languages**
    (toggle each `wordlists/*.yml`, show word counts and review-status in the lore), **word browser** (paginated view of a language or custom list, with add/remove for custom words and the whitelist),
    **ladder editor** and **mute list** if feasible (else document read-only), **world profiles**. Changes are staged: nothing is written until the player clicks **Done** (save with the comment-preserving
    editor, then reload); **Cancel** discards; unsaved-changes warning on close. Pure functions `render(menuState) -> List<Slot>` and `click(slot, clickType, player) -> MenuAction`, so the whole GUI is unit-testable
    with simulated click sequences. Every click re-checks the permission `antibadwords.gui` (default op) and per-setting edit rights.
B2. **Text input** abstraction `TextPrompt` with implementations chosen by capability: (1) **sign editor** (preferred, as the project owner asked): Paper/Purpur via the player-sign API, Spigot if its API has it,
    Fabric/Forge by sending a temporary client-side sign (block update + block-entity data + open-sign-editor packet) and reading the answer in the existing sign mixin (intercept only for players with an
    active prompt, cancel, restore the real block state); the lines are joined with spaces, line/length limits explained in the prompt text; (2) **chat prompt** fallback: the player's next chat message is captured,
    never broadcast, never scanned or stored, `cancel` aborts, 60 s timeout (configurable). Validate against the setting type, min/max, regex; show a clear error and re-prompt. Secrets are only accepted through
    these prompts and are never echoed.
B3. **Platform renderers.** Bukkit/Spigot/Paper/Purpur: a normal chest `Inventory` GUI with item icons (material mapping in a data file so admins/devs can tune it), cancel every click/drag/shift-click/number-key,
    handle close and quit. Fabric/Forge: a **server-side** chest menu (vanilla `MenuType` 9x6 with a custom container; no client mod needed) with the same engine model; override click handling so no item can be
    taken, handle close/disconnect. Read the real 26.x and 1.21.11 APIs with `javap`; expect differences between versions and use small per-version shims like `src/mc-variants`.
B4. **Entry points.** `/abw` with no arguments, run by a player with `antibadwords.gui`, opens the main menu (console and players without permission keep today's behaviour); also `/abw gui [page]`. The main menu shows
    status (enabled languages, protection level, scan sources on/off, active profile) and a button per category; a search-by-typing button jumps to a setting. Add the permission to `plugin.yml`, Forge nodes and Fabric.
B5. **Tests.** Engine unit tests for the model (toggle, numeric step, enum cycle, list edit, staged save/cancel, validation errors, pagination edge cases, secret masking, permission checks). Extend
    `5-shared-engine/live-player-test/bot.js`: open `/abw`, read window titles/items, click through to a setting, toggle it, click Done, verify the config file changed and `/abw reload` kept it; test the chat-prompt
    path end to end; test the sign path where the bot library allows (otherwise a documented manual checklist). Run on Fabric and Forge 26.1 and 1.21.11 and on the Bukkit test server from session 2 if it exists.
B6. **Docs.** `6-guides/GUI.md` (ELI5 with a menu map), update `CONFIGURATION.md`, `README.md`, folder READMEs, `CHANGELOG.md`, and the honest test matrix.

## Files
Part A: create `5-shared-engine/src/main/java/com/jason/plugins/antibadwords/engine/discord/**`, a Bukkit-only class in `2-plugin-bukkit-purpur/src/main/java/com/jason/plugins/antibadwords/` for the DiscordSRV hook,
`plugin.yml` (`softdepend: [DiscordSRV]`), `6-guides/DISCORD.md`. Part B: create `5-shared-engine/src/main/java/.../engine/gui/**`, `2-plugin-bukkit-purpur/.../gui/**`, `5-shared-engine/src/mc/java/.../gui/**`, per-version shims in
`5-shared-engine/src/mc-variants/`, `6-guides/GUI.md`. Both: registry registrations, `engine.commands()`, `ModerationEvent` subscribers, tests, bot, docs. Read first: the session 2-4 engine code and `7-wordlists/README.md`.

## Constraints
* Discord moderation and Discord-to-Minecraft filtering exist **only through DiscordSRV on the Bukkit family** (decided with the owner); webhooks work everywhere. Never claim otherwise.
* Secrets: never in logs, tests, history, GUI text or git; mask everywhere; fail closed on any permission doubt.
* Everything configurable; the GUI must not hard-code any setting, it renders the registry. Settings that cannot be edited in the GUI must say so in their description and stay editable in the files.
* No new libraries except SnakeYAML/Gson (use `java.net.http` for HTTP). Do not rename earlier sessions' APIs. Keep the 24-jar build and naming.
* Mixin and API rules: section 3 of the shared context. Sign editing and chest menus differ per Minecraft version; verify on all six versions, not only the newest.

## Acceptance criteria
- Unit tests pass (`5-shared-engine`, plugin); new tests cover webhook queue/429/masking and the menu model with simulated clicks.
- `TEST=1 ./build-everything.sh` (all 24 jars) is clean; the scripted player opens the menu, changes a setting through the menu, saves, and the change is live; a violation produces a Discord webhook post
  against a fake local server (Part A) on all four server types.
- DiscordSRV hook: loads without DiscordSRV, works with the installed API if you can obtain it (say exactly what you could and could not test).
- Docs complete and honest; known limits listed.

## Branch
Part A: `handoff/session-5a-discord`; Part B: `handoff/session-5b-gui` (or one branch `handoff/session-5-discord-gui` if run as one session). Branch from `main` **after sessions 2 and 4 are merged** (3 preferred).
Open pull requests to `main`.

## Report back
Per part: what changed, files, every new setting/command/permission, test results per loader and version, what could not be tested (DiscordSRV live, sign input, Spigot-only paths), security review notes
(secrets, permissions, rate limits), screenshots or a text map of the menu, open issues and follow-ups.

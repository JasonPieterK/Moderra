# live-player-test - a robot player that swears

`bot.js` joins a test server (offline mode) as "Tester" and checks, with 15 checks, that Moderra reacts correctly to:
clean and swearing chat, a disguised `f.u.c.k`, `/msg` with and without a swear word, a book, a sign and an anvil rename.
It prints `LIVE PLAYER TEST PASSED` or `FAILED`.

```bash
npm install
node bot.js localhost 25565 26.1 extras      # host, port, Minecraft version, "extras" = also books, signs, anvils
```
"extras" needs operator rights and items, which only the server console can give: the bot prints lines starting with
`CONSOLE> ` and expects whoever launched it to type them into the server console (the launcher scripts used while
developing did that automatically).

Limits: it only works for Minecraft versions the `mineflayer` library supports. 26.1, 26.1.1, 26.1.2 and 1.21.11
work (use `26.1` as the version for the two hotfixes); 26.2 and 26.3 do not yet.

New in this round (written but **not run yet**, see `handoffs/BUILD-VERIFICATION-TODO.md`): with "extras" the bot also
tries `/moderra lang list`, `/moderra lang enable de` / `disable de` and `/moderra whitelist add` / `list` / `remove` (7 more checks).
A world-profile test is not in the bot (it would need two worlds and a prepared `worlds.yml`); profile rules are covered
by the engine's unit tests.

## filters mode (spam, links, bypass tricks, player names) - written, **never run**

```bash
node bot.js localhost 25565 26.1 filters           # or "extras,filters"
BOT_NAME=xXshitXx node bot.js localhost 25565 26.1 filters   # also tests the join-name warning
```

14 more checks (15 with `BOT_NAME`): a swear word with a zero-width character or a Cyrillic look-alike inside, an
invisible character without a swear word, a plain link, `example dot com`, `example[.]com`, a normal sentence, capital
letters, a character flood, the same message four times, eight messages in two seconds, and the join-name warning.

The new filters are **off** by default, so before the server starts put this into its config folder
(`plugins/Moderra/` or `config/moderra/`); start the server once first so the files exist:

| File | Change |
|------|--------|
| `filters/spam.yml` | `filters.spam.enabled: true` |
| `filters/links.yml` | `filters.links.mode: block-all` |
| `filters/evasion.yml` | `filters.evasion.detect.action: block` |
| `config.yml` | `filters.scan.join-names: true` and `filters.join-names.action: warn` (only for the `BOT_NAME` run) |
| `moderation/ladder.yml` | `punishment.violations-before-punish: 50` (so the bot is not punished half-way) |

On Bukkit, Spigot, Paper and Purpur a "censor" action rewrites the chat line; on Fabric and Forge it blocks the line.
The bot accepts both. With `BOT_NAME` the basic checks that look for the name "Tester" in broadcasts may fail; look only
at the join-name line of that run.

## moderation mode (strikes, ladder, mute, history) - written, **never run**

```bash
node bot.js localhost 25565 26.1 moderation          # part 1; needs operator rights (the console op line is printed)
# reconnect, or stop and start the server, then:
node bot.js localhost 25565 26.1 moderation-again    # part 2: still muted? strikes still there? history kept?
```

Part 1 checks `/moderra strikes`, the ladder steps (warn, mute), the mute blocking chat, `/msg` and a sign, the notice with the
time left, `/moderra mutes`, `/moderra history`, the mute ending by itself and a manual `/moderra mute`. Part 2 checks the notice on
join, that the mute and the strikes survive the reconnect or restart, `/moderra unmute` and that the history is kept.
Prepare the server before it starts (config folder as above):

| File | Change |
|------|--------|
| `config.yml` | `punishment.clear-violations-on-quit: false` (otherwise strikes are not kept) |
| `moderation/ladder.yml` | `ladder.steps`: `warn-step` at 2 with `warn`, `mute-step` at 3 with `mute 20s bot test`, `kick-step` at 50 with `kick` (so the bot is not kicked half-way); `strikes.decay.every: 0s` |
| `moderation/mute.yml` | `mute.messages.blocked` and `.join` left at their defaults (the bot matches "You are muted" / "You are still muted") |

Written without a server: texts and timings may need small adjustments. Checked with `node --check` only.

## Mini tasks 9 and 10: Discord webhook alerts (session 5, A1) - engine only

Verification level: engine unit tests run (`cd 5-shared-engine && mvn -q -B test`; 20 new tests in `engine/discord/DiscordWebhookTest`
against a local `com.sun.net.httpserver`). Adapters not changed, nothing run against real Discord.

### Added (package `engine.discord`)
- `DiscordSettings` (`discord.yml`, category "Discord"): `discord.enabled` (false), `discord.mode` (auto|webhook|discordsrv|off),
  `discord.webhook.alerts-url` / `log-url` (SECRET, `${ENV:NAME}` allowed as the whole value), `webhook.username`,
  `routing.alerts` / `routing.log` (kind lists, `*` = all), `style` (embed|plain), `colors.<kind>`, `templates.<kind>`, `mention`,
  `mention-kinds`, `queue.size`, `rate.min-interval`, `rate.batch-size`, `rate.batch-wait`, `http.timeout`, `http.retries`,
  `http.backoff`, `http.shutdown-wait`. Kinds: violation, punishment, mute, unmute, spam, link, evasion, admin-command.
  Permission constant `DiscordSettings.PERMISSION` = `antibadwords.discord` (adapters must declare it: plugin.yml, ModPermissions, ForgePermissions).
- `DiscordService` (`engine.discord()`): subscribes to `engine.events()`, installed in `Engine.registerBuiltIns`; `shutdown()` is called first thing in
  `Engine.shutdown()`. `active()`, `suppressWebhooks(boolean)` (for the DiscordSRV hook later: set true while DiscordSRV carries the alerts),
  `mask(text)`, `setEnvironment(fn)` (tests). `mode` auto currently = webhook (DiscordSRV is not implemented here; the Bukkit task should
  call `suppressWebhooks(true)` when it takes over, and treat `discordsrv` mode as "webhook sender off").
- `WebhookSender` (one per webhook, own daemon thread started on first message): bounded queue (drop oldest + one warning per 30 s with a
  running total), batching (`batch-size` messages or `batch-wait`), `min-interval` between requests, 429 `Retry-After` (header seconds, decimals
  ok, or JSON `retry_after`, capped at 60 s; also `X-RateLimit-Remaining: 0` + `Reset-After`), retries with doubling backoff for 5xx / IO errors,
  final 4xx not repeated. `java.net.http` only. Shutdown: one attempt per waiting batch, no waiting for backoff or rate limits, joined for at most
  `http.shutdown-wait`, rest dropped with a warning.
- `Secrets.mask(text, secrets)`: hides URLs as written, as resolved, the token part and anything that looks like `.../webhooks/...`. Used for every
  warning, `/abw discord` output and error text. `Setting.display` already masks in the registry/docs.
- `/abw discord test|status` (`DiscordCommand`, permission `antibadwords.discord`). `test` sends to every usable webhook ahead of the queue and
  reports back on the server thread via `platform.runSync`; it also works while `enabled: false` (says so). `status` shows queue/sent/failed/dropped.

### Decisions
- Player-controlled text in placeholders is neutralised (`@` gets a zero-width space, markdown characters escaped, control chars removed, 300 chars
  max) and `allowed_mentions` only allows what the admin wrote in `discord.mention` (parse is `[]` otherwise), so players cannot ping.
- Mention goes only to the alerts webhook, never to the log webhook. Routing a kind to both webhooks is allowed.
- URLs must be https; plain http is accepted only for localhost/127.0.0.1/::1 (tests, local relay). Unusable values are reported on reload without printing them.
- World-readable warning only when a literal URL is in `discord.yml` (POSIX permissions; silently skipped where unsupported).
- Placeholders: `{player} {word} {source} {world} {count} {limit} {action}` plus `{kind} {check} {detail} {step} {reason} {duration} {command} {text}`;
  `{action}` falls back to punishments / actions / command. Unknown placeholders stay literal.
- Colours are one STRING setting per kind (`#RRGGBB`), not a map section, so they appear in the GUI and docs; an invalid colour falls back to grey.
- Settings are read with `engine.value` (not per-profile).

### Left for later
- DiscordSRV hook, adapters (permission declaration), `/abw info` line about Bukkit-only DiscordSRV, `6-guides/DISCORD.md`, README/CHANGELOG.
- The `mute` / `unmute` events are only routed here; the mute task must publish them (kinds `mute` / `unmute`, data `reason`, `duration`).

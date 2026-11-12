# Session 3/5 (medium): New filters - spam, evasion, links, and more scanned sources

## Summary
Add four detection features on top of the engine from session 2: **(1) anti-spam and flood control**, **(2) invisible-character and look-alike (homoglyph)
detection**, **(3) link / IP / Discord-invite filter**, and **(9) more scanned sources**. Each is a `TextCheck` registered with the engine pipeline, fully
configurable by admins through the settings registry and its own file under `filters/`, produces `ModerationEvent`s, and works identically on the plugin
and on Fabric/Forge. Prerequisite: session 2 is merged (`Engine`, `TextCheck`, `SettingsRegistry`, `ModerationEvent`, folder config). Read
`handoffs/00-shared-context.md` first.

## Tasks
1. **Evasion normalisation (feature 2)**, `filters/evasion.yml`. A configurable normaliser that runs *before* word matching and feeds the existing `WordMatcher`:
   strip zero-width / bidi-control / soft-hyphen / variation-selector characters, Unicode NFKC fold (full-width, compatibility forms), diacritics folding (switchable
   per script because it must not damage languages that need them), a **confusables table** (Cyrillic/Greek/other look-alikes to Latin, and common leetspeak) loaded
   from a data file the admin can extend (`filters/confusables.yml`, with a sane default based on Unicode confusables data; check and credit its licence in `NOTICE`),
   repeated-character collapse, separator handling. Each step toggles individually; unknown/empty config falls back to safe defaults. Add unit tests with real bypass
   attempts (zero-width inside a word, Cyrillic "a/e/o" mixed into Latin, full-width text, combining marks) and a no-false-positive test per language family.
   Add a **performance budget**: with 10 000 words loaded, checking a 256-character message must stay under 1 ms at the 99th percentile on a normal laptop; the
   current `lookupPartial` scans all entries per token, so if partial matching is on, introduce an Aho-Corasick (or equivalent) index. Include a small JMH-free
   timing test or fuzz harness (random and nasty inputs; no crashes, no slow cases).
2. **Spam and flood control (feature 1)**, `filters/spam.yml`. Checks, each with its own on/off, threshold and action: messages per time window (rate limit),
   repeated or near-identical messages (hash plus similarity ratio, window and ratio configurable), too many capital letters (percentage, minimum length),
   character flood (same character N times, symbol/emoji spam), too many identical words, command spam (separate window). Actions per check: `block` (with a
   configurable message), `censor` (rewrite the text, for example lower-case the caps), `log-only`; strike weight per check (consumed by session 4's strike system, via
   `Hit.strikeWeight`, default 0 so spam never punishes unless the admin chooses); bypass permission `antibadwords.bypass.spam`; per-source switches. Per-player
   state is kept in memory with bounded size and cleaned on quit/expiry. Emit `spam` events.
3. **Link filter (feature 3)**, `filters/links.yml`. Detect URLs, bare domains, IPv4/IPv6, Discord/other invite links, and obfuscated forms ("example dot com", "exa mple . com",
   "[.]", unicode dots, missing scheme). Modes: `block-all`, `allow-list` (allowed domains/suffixes, wildcards, admin-editable list), `block-list`; separate switches for IPs and
   invites; actions and strike weight as in spam; bypass `antibadwords.bypass.links`; applies to the sources the admin selects (chat, signs, books, commands). Emit `link` events.
   Provide a default allow-list that is empty and a default mode that is off (admins opt in).
4. **More scanned sources (feature 9)**, `config.yml` scan switches plus registry entries: (a) **join username check**: when a player joins, check the name against the filter,
   action configurable (`log`, `warn`, `kick` with a configurable message), bypass permission; (b) **sign back side** and every sign line on both sides on all four loaders
   (check how the Bukkit `SignChangeEvent` exposes sides, and extend the Fabric/Forge sign mixin and the shim for the packet's front/back flag); (c) **command-block text**
   (the packet a player sends when editing a command block, via a mixin on the right handler on Fabric/Forge, and the matching Bukkit event if one exists; document
   what is not possible); (d) a broader default `scanned-commands` list (for example `team`, `title`, `tellraw`, `bossbar`, `scoreboard`, `nick`, `mail`, `broadcast`, `rename`)
   still fully editable. Each new source gets a scan on/off switch and an entry in `/abw info`. Do not invent sources that the platform cannot provide; list the limits in
   the docs.
5. **Pipeline order and docs.** Make the check order configurable (`config.yml: filters.check-order`), default: evasion-normalise, badword, link, spam. Add every setting
   to the registry with titles and descriptions (these become GUI labels in session 5), regenerate `6-guides/CONFIGURATION.md`, add `/abw check <text>` output that names which
   check fired and why.
6. **Tests.** Unit tests for each check (with fake clocks), the normaliser, the link parser (lots of obfuscation cases), the pipeline order, per-profile overrides from
   `worlds.yml`. Extend `5-shared-engine/live-player-test/bot.js` with spam (fast repeats, caps, flood), link (plain and obfuscated), evasion (zero-width, mixed scripts)
   and the join-username check where the library allows, and run it on Fabric and Forge 26.1 and 1.21.11 and on the Bukkit test server if session 2 created one.

## Files
Create under `5-shared-engine/src/main/java/com/jason/plugins/antibadwords/engine/filters/**` (`EvasionNormalizer`, `SpamCheck`, `LinkCheck`, `Confusables`, ...), tests under
`5-shared-engine/src/test/`, data `filters/confusables.yml` (shipped as a jar resource and copied to the config folder). Modify: `WordMatcher.java` (index for partial match
and normaliser hook), the adapters (`2-plugin-bukkit-purpur/.../AntiBadWordsListener.java`, `5-shared-engine/src/mc/java/.../ModRuntime.java`, the Fabric/Forge mixins and the
`src/mc-variants` sign shims), `6-guides/CONFIGURATION.md`, `README.md`, `CHANGELOG.md`, `5-shared-engine/live-player-test/bot.js`.

## Constraints
* Everything configurable; defaults must keep today's behaviour for existing installs (new filters off or log-only by default except evasion normalisation, which is on but
  must not change results for clean text). No new libraries except SnakeYAML/Gson.
* Do not implement strikes, mute, history or Discord: those are sessions 4 and 5. Only fill `Hit.strikeWeight` and emit events.
* Do not change the settings registry, config-loader or event APIs from session 2 in an incompatible way; if you must extend them, make the extension additive and document it.
* Mixin rules and per-version API differences: see section 3 of the shared context. Read real APIs with `javap`.
* Keep the 24-jar build and naming. Session 4 may run in parallel on moderation code: avoid editing `StrikeService`, mute, ladder or history code; if the adapters conflict, rebase on `main` often.

## Acceptance criteria
- `cd 5-shared-engine && mvn test` and the plugin's `mvn clean test ...` pass, including the new tests and the performance test (state the measured numbers).
- `TEST=1 ./build-everything.sh 26.3 26.1 1.21.11` is clean; scripted-player checks for spam, links, evasion pass on Fabric and Forge 26.1 and 1.21.11.
- Each feature can be switched off completely and every threshold changed from the config files; `/abw reload` applies it; invalid values give a clear error and keep the old value.
- A full `TEST=1 ./build-everything.sh` (all 24 jars) is clean before the PR, or you list exactly what failed.

## Branch
`handoff/session-3-filters`, branched from `main` **after session 2 is merged**. Open a pull request to `main`.

## Report back
What each check does and its default, the full list of new settings (key, default), measured performance numbers, the false-positive tests you ran, platform limits you found
for sources (b)-(c), test results per loader/version, open issues.

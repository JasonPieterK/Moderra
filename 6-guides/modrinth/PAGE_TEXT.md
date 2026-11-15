# Modrinth page text - Moderra

Copy section by section into the Modrinth description editor. Formatting hints are in `[brackets]`; do not
paste the brackets. `[SHOT NN]` = the gallery/inline image from [SCREENSHOT_PLAN.md](SCREENSHOT_PLAN.md).
Items marked **TODO** need Jason's input. Modrinth supports Markdown, tables, images and `<details>` dropdowns.

---

## 0. Top of page

[Banner image, centred - TODO logo/banner]

[Heading 1, centred] **Moderra**

[Italic, centred] Keep your chat clean - in seven languages, everywhere players can type.

[Row of badges or plain bold text, centred]
**Spigot** · **Paper** · **Minecraft 1.21.11 - 26.3** · **MIT licensed** · **Free**

---

## 1. Short pitch (no heading; normal text size, first two sentences bold)

**Moderra blocks profanity before anyone sees it, warns the player, and punishes repeat offenders.
It works in chat, commands, signs, books and anvil renames, and it is built to stop the usual tricks players
use to get around filters.**

No more `f u c k`, `sh1t` or `fuuuuuck` slipping through - and no more harmless words like `closer` or `class`
getting blocked by mistake.

[SHOT 01 - the featured image, full width]

---

## 2. Why Moderra? [Heading 2]

[Bullet list, bold lead-ins]

- **Smart, not just strict.** Matches whole words, so innocent words stay safe. Opt in to stricter partial
  matching when you want it.
- **Hard to bypass.** Spaced letters, leetspeak, `f*ck`, repeated letters, accents, invisible characters and
  look-alike letters are all handled.
- **Seven languages built in.** Over 170 words across English, Indonesian, Portuguese (Brazil), Russian,
  UK English, German and Chinese - including real Cyrillic and Chinese characters, not only transliterations.
- **Covers everything players can type.** Chat, selected commands, signs, books (pages and title) and anvils.
- **Real punishments.** Potion effects, temporary attribute changes, sounds and console commands.
  Attribute changes are always restored - on timer end, quit, kick, respawn, server stop, or after a crash.
- **Made for staff.** Live alerts, `/moderra check`, per-player violation counters and a bypass permission.
- **One config, no restart.** Everything lives in `config.yml`; `/moderra reload` applies it instantly.

---

## 3. Where it protects [Heading 2]

[Table with an image column, or five small sections with one image each]

| Source | What happens | Image |
|--------|--------------|-------|
| **Chat** | Message is cancelled, player warned | [SHOT 01] |
| **Commands** | Arguments of `/msg`, `/tell`, `/me`, `/say`, `/mail`... are scanned (list is configurable, or scan all) | [SHOT 07] |
| **Signs** | Placing text with a bad word is cancelled | [SHOT 04] |
| **Books** | Every page and the title are checked | [SHOT 05] |
| **Anvils** | Renaming an item with a bad word gives an empty result; counted once per anvil session, no spam | [SHOT 06] |

---

## 4. Punishments [Heading 2]

[One short paragraph, normal size]
Each player gets a strike for every blocked message. At the strike limit (default **2**) the plugin
punishes them. Turn on any combination of the actions below.

[SHOT 02 and SHOT 03, side by side or one after another]

[Four sub-sections, Heading 3, each with 1-2 lines]

**Potion effects** - Blindness, slowness, weakness, nausea, mining fatigue, glowing, or any effect.
You pick the level and the duration.

**Attribute changes** - Slow a player's movement, weaken their attacks, lower max health, shorten their
reach, reduce jump strength... for a few seconds. The original value is always put back.

**Sounds** - Play any Minecraft sound to the offender (the villager "no" is on by default).

**Console commands** - Mute, kick, jail, or anything else your server offers, with `{player}`.
Plus an optional *final command* after all actions.

[Callout/quote] Punishments are all **off by default except one harmless sound**, so nothing surprising
happens the first time you install it.

---

## 5. Hard to bypass [Heading 2]

[SHOT 08 - full width]

[Table - left column in `code` style, right column normal text]

| Players try... | Moderra sees it |
|----------------|----------------------|
| `f u c k` or `f.u.c.k` | Letters separated by spaces or dots |
| `sh1t`, `sh!t`, `$hit`, `a$$` | Leetspeak and symbols |
| `f*ck` | Masked letters |
| `fuuuuuck`, `assssshole` | Stretched letters |
| `ＳＨＩＴ` (full-width) | Unicode look-alikes |
| invisible characters inside a word | Stripped before matching |
| Cyrillic letters inside a Latin word | Mapped back to Latin |
| `fick dich`, `filha da puta` | Multi-word phrases, with or without spaces |

[Small text] Optional extras: glue neighbouring words together, or block words that merely contain a bad
word. Both are off by default because they cause false positives.

## 6. No false alarms [Heading 2]

[SHOT 09]

Most filters block `class` because it contains "ass". Moderra compares whole words, so `closer`,
`scrap`, `peacock`, `cocktail` and `computer` all pass. A built-in whitelist protects you even if you turn
partial matching on, and you can add your own words.

---

## 7. Seven languages [Heading 2]

[SHOT 10]

| List | Notes |
|------|-------|
| English | |
| UK English | `bellend`, `tosser`, `wanker`... |
| Indonesian | slang and abbreviations (`kntl`, `gblk`...) |
| Portuguese (Brazil) | accents optional |
| Russian | Latin transliteration **and** Cyrillic |
| German | |
| Chinese | Hanzi **and** pinyin |

Every list is split into **three severity levels** (mild / rude / severe). Set `protection-level` to
**1 = LOW** (severe only), **2 = MEDIUM** or **3 = HIGH** (everything).

Add your own words with `/moderra addword <word or phrase>` - any language, accents and spaces allowed.

---

## 8. For staff and admins [Heading 2]

[SHOT 11 and SHOT 12]

- **Live alerts** for every blocked word (permission `moderra.notify`), also logged to the console.
- **`/moderra check <text>`** tells you instantly whether a text would be blocked - perfect for testing your
  word list.
- **Violation counters** with optional expiry (e.g. forget after 30 minutes).
- **Bypass permission** `moderra.bypass` for owners or trusted players.
- Tab completion for sub-commands, players and your custom words.

### Commands and permissions [Heading 3]

| Command | Permission | Description |
|---------|-----------|-------------|
| `/moderra reload` | `moderra.admin` | Reload config |
| `/moderra addword <word>` | `moderra.admin` | Add a custom word or phrase |
| `/moderra removeword <word>` | `moderra.admin` | Remove a custom word |
| `/moderra listwords [list]` | `moderra.admin` | Show a word list |
| `/moderra check <text>` | `moderra.admin` | Test a text |
| `/moderra violations <player>` | `moderra.admin` | Show a player's strikes |
| `/moderra resetviolations <player>` | `moderra.admin` | Clear a player's strikes |
| `/moderra info` | `moderra.admin` | Status summary |

There are no aliases. Other permissions: `moderra.notify` (default: op), `moderra.bypass`
(default: nobody).

---

## 9. Easy to configure [Heading 2]

[SHOT 13 and SHOT 14]

[Collapsible `<details>` block titled "Example: punish with blindness + slowness"]

```yaml
punishment:
  enabled: true
  violations-before-punish: 2
  effects:
    blindness:
      enabled: true
      effect-name: "minecraft:blindness"
      amplifier: 0
      duration: 3
    slowness:
      enabled: true
      effect-name: "minecraft:slowness"
      amplifier: 2
      duration: 3
```

[Collapsible block "Example: temporary mute through your permissions plugin"]

```yaml
punishment:
  final-command:
    enabled: true
    command: "lp user {player} permission settemp moderra.muted true 30s"
```

[Collapsible block "Messages and placeholders"]
Every message supports `&` colour codes and `{player}`, `{word}`, `{source}`, `{count}`, `{limit}`,
`{remaining}`, `{punishments}`, in any capitalisation.

---

## 10. Compatibility [Heading 2]

| Minecraft | Java on the server |
|-----------|--------------------|
| 1.21.11 | 21 or newer |
| 26.1, 26.2, 26.3 | 25 |

- **Software:** Bukkit, Spigot, Paper and Purpur (Purpur has its own download; same code). *(Test on a Purpur server before publishing - TODO.)*
- Not for Folia. Fabric and Forge versions are a separate download once they have been tested.
- Uses only the standard Bukkit/Spigot API, so it needs no other plugins or libraries.
- **Download the file whose name matches your Minecraft version**, e.g. `Moderra26.3-1.0-paper.jar`.
- Only full releases from the last year are published; snapshots are not supported.

## 11. Installation [Heading 2]

1. Download the jar for your Minecraft version.
2. Put it in your server's `plugins` folder.
3. Start the server. `plugins/Moderra/config.yml` is created.
4. Edit the config, then run `/moderra reload`.

## 12. Upgrading from 1.00 [Heading 2]

Your old config still works: missing options fall back to defaults. Note that **protection levels were
inverted in 1.00** (LOW blocked only mild words). In 1.10 higher means stricter. Check the value of
`protection-level` after updating. You may also want to copy the new options from the default config.

---

## 13. FAQ [Heading 2, then `<details>` for each]

**Does it work with LuckPerms / EssentialsX / other chat plugins?**
It listens to the standard chat event at the highest priority and cancels bad messages, so it works alongside
most chat plugins. Plugins that rewrite or send chat themselves without the event can't be filtered.

**Can staff swear?**
Give them `moderra.bypass`.

**A normal word is being blocked.**
Add it to `filters.whitelist`, or run `/moderra check <word>` to see which list entry matches.

**Does it log to a file?**
Blocked words are written to the server console (and therefore the normal server log).

**Does it store player data?**
No database. Strike counts are in memory. The only thing written to player data is a temporary backup of an
attribute value while an attribute punishment is active, removed again afterwards.

**Does it lag my server?**
Words are indexed once when the config loads; checking a message is a quick lookup.

## 14. Support and links [Heading 2]

- Source code: https://github.com/JasonPieterK/AntiBadWordsPlugin
- Report a bug: GitHub Issues
- Changelog: https://github.com/JasonPieterK/AntiBadWordsPlugin/blob/main/CHANGELOG.md
- Discord: **TODO** (optional)

[Small, centred] Made by Jason P · Licensed under MIT

---

## Notes for Jason before publishing

- Everything on the page matches what the code does, **except that it has never been run on a real server**.
  Do the test list in [UPLOAD_HANDOFF.md](UPLOAD_HANDOFF.md) before publishing.
- Section 0 (banner/logo; Jason decided to skip the logo for now) and the "Purpur" mention are waiting on you.
- Also test once on real Spigot and real Paper before claiming both.
- Section 12 matters: the old inverted protection level is a real upgrade trap.
- Hide any severe words in screenshots if you want a family-friendly page (the plan suggests mild words).

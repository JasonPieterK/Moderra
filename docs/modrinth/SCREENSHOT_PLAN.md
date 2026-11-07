# Screenshot plan (gallery)

You take these in Minecraft; the page text in [PAGE_TEXT.md](PAGE_TEXT.md) refers to them by ID
(`[SHOT 03]`). 14 shots + 1 optional GIF.

## Setup once

- Test server: Paper 1.21.11 or 26.x, `online-mode=false` is fine for a second account.
- Two accounts: **Player** (the one who swears, no op) and **Admin** (op). Two game windows, or take the
  Player shots first, then the Admin shots.
- Video settings: 1920x1080 (or 1280x720), GUI scale 2-3, `F1` hides the hotbar when you want clean chat,
  brightness/time = day, no shaders. Keep each image under Modrinth's gallery size limit (about 5 MiB;
  PNG is fine, JPG if too big).
- Use a short, readable chat history: clear it with a relog between shots. Use `F3 + D` to wipe chat.
- For the punishment shots, enable these in `config.yml` first, then `/abw reload`:
  `punishment.effects.blindness`, `slowness`, `nausea`, `punishment.attributes.reduce_speed`,
  `punishment.sounds.villager_no` (already on), `violations-before-punish: 2`.
- Keep the real bad words in shots **readable but not gratuitous**. Use mild words (`stupid`, `idiot`,
  `damn`) wherever the shot doesn't need a severe one.

## Gallery shots

| ID | Filename | What to show | How to stage it |
|----|----------|--------------|-----------------|
| 01 | `01-hero-chat-blocked.png` | **Featured image.** Chat blocked + warning. | Player types a mild bad word. Capture the red broadcast and the yellow personal warning together. Chat open (`T`) with a blurred/dim background looks best. |
| 02 | `02-punishment-broadcast.png` | The punishment broadcast. | Player swears a 2nd time. Capture the "...FOR THEIR ACTION THEY'LL BE PUNISHED WITH ..." line. |
| 03 | `03-punishment-effects.png` | Visible effects. | Right after punishment: blindness/nausea on screen, the potion icons top-right. Take it a second after the punishment. |
| 04 | `04-sign-blocked.png` | Sign filtering. | Place a sign, type a bad word on a line, close the editor. Capture the sign screen with the warning in chat. |
| 05 | `05-book-blocked.png` | Book filtering. | Book & quill, bad word on page 1, press Done/Sign. Capture the book plus the warning. |
| 06 | `06-anvil-blocked.png` | Anvil rename blocked. | Rename a sword with a bad word. Capture the anvil GUI with the **empty result slot** and the warning. |
| 07 | `07-command-whisper.png` | Command arguments scanned. | `/msg Admin <bad word>` as Player. Capture the cancelled command and warning. |
| 08 | `08-bypass-attempts.png` | Smart matching. | Admin runs `/abw check` on several tricks one after another (see below) so all results show in one chat screen. |
| 09 | `09-no-false-positives.png` | No Scunthorpe problem. | Admin: `/abw check closer`, `/abw check scrap metal`, `/abw check peacock`, `/abw check class`. All "Clean". |
| 10 | `10-multilingual.png` | Multi-language. | Admin: `/abw check` with one word per language: `bodoh` (id), `idiota` (pt), `дурак` (ru), `dumm` (de), `傻逼` (zh). Needs a client font that shows Chinese/Cyrillic (vanilla does). |
| 11 | `11-staff-notify.png` | Staff alerts. | Admin window while Player swears: the grey `[AntiBadWords] Blocked '...' from ... (1/2)` line. |
| 12 | `12-admin-commands.png` | Admin command set. | Admin: `/abw` then `/abw info`. Capture tab-completion list (type `/abw ` and press Tab) plus the `info` output. |
| 13 | `13-config-file.png` | Config. | Screenshot of `config.yml` open in an editor (VS Code / Notepad++, dark theme) at the `punishment` section. |
| 14 | `14-console-startup.png` | Console banner + log. | Server console right after start: the ASCII banner, "Loaded N blocked words" and a blocked-word log line. |
| 15 (optional GIF) | `15-demo.gif` | 10-15 s demo. | Screen-record: type bad word, warning, type again, punishment effects. Keep under ~8 MB. |

### Strings for shot 08 (`/abw check ...`)

```
/abw check f u c k
/abw check f*ck
/abw check sh1t
/abw check fuuuuuck
/abw check ＳＨＩＴ    (full-width letters)
```

Only use ones that your own test confirms are "Blocked". If one isn't, don't show it, and tell Bram so
it can be fixed.

## Other images (not screenshots)

| Asset | Size | Notes |
|-------|------|-------|
| Icon / logo | 512x512 PNG | **Waiting for Jason** (logo not started). Modrinth icon is shown at small sizes: keep it simple. |
| Banner (optional) | about 1200x300 | Name + tagline, same style as logo. Use at the top of the page. |
| Divider / feature icons (optional) | 64x64 | One small icon per feature row if you want them. |

## Gallery order and captions (title / description fields on Modrinth)

1. 01 - "Bad words never reach chat" / "The message is cancelled and the player is warned."
2. 02 - "Repeat offenders get punished" / "Effects, attribute changes, sounds and commands, all configurable."
3. 03 - "Visible consequences" / "Blindness, nausea, slowness... applied for a few seconds."
4. 08 - "Hard to bypass" / "Spaced letters, leetspeak, masked words and repeated letters are caught."
5. 09 - "No false alarms" / "Whole-word matching: `closer`, `scrap` and `class` are fine."
6. 10 - "7 languages" / "English, Indonesian, Portuguese, Russian, UK English, German, Chinese."
7. 07, 04, 05, 06 - "Everywhere players type" / chat, commands, signs, books and anvils.
8. 11, 12 - "Made for staff" / alerts, `/abw check`, violations.
9. 13, 14 - "Simple to configure" / one `config.yml`, reload without restart.

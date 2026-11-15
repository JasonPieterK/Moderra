# Moderra logo concepts (checkpoint round)

## Assumptions
Moderra is a chat-moderation plugin/mod for Minecraft servers (Paper, Spigot, Bukkit, Purpur, Fabric, Forge): 37-language bad-word filter, spam/link/trick detection, strikes, mutes, punishment ladder, history, Discord alerts, in-game menu. Audience: server owners and staff. Feel: trustworthy, calm authority, modern, clear. Palette: the project's Bauhaus system (red #D6352B, blue #1E4FA3, yellow #F2C230, black #111111, off-white #F4EFE6), flat geometry. Main use: Modrinth icon (square, readable at 16-32 px, PNG < 256 KiB). Avoid: padlocks, Minecraft textures/Mojang marks, #@! clichés, Discord/Modrinth look-alikes. Category clichés (library check, security/identity): shields, containment shapes, red. Chat-bubble marks in the library (Twitch, WhatsApp, Disqus, Mastodon) all fuse a bubble with another object; none is a flat blue bubble with a redacted line.

Longlist (10): 1 bubble with redacted line; 2 M from pillars and wedge; 3 flat shield + typing bubble; 4 stacked strike steps (red/yellow/blue); 5 funnel triangle filtering circles; 6 circle-square-triangle trio as escalation; 7 bubble split by a gate bar; 8 prohibition circle with bubble (cliché, dropped); 9 M formed from a bubble tail; 10 gavel (dropped, literal). Built: 1, 2, 3.

Files use a full-bleed off-white square (works on Modrinth light and dark). Fonts: none, no text in any mark. PNGs rendered with Playwright Chromium (render_png.py's Chrome backend hung in this sandbox).

## A - Bubble Gate (recommended)
- Idea: a chat bubble with one line blocked out in red, the filter at work.
- Rationale: says "chat" and "moderation" in one glance; the red bar is the only accent, so it is calm, not alarming; flat blocks and a triangle tail are pure Bauhaus; no shield cliché; the evolution of the current bubble.
- Mark type: pictorial/abstract symbol (no letters).
- Colours: blue #1E4FA3, red #D6352B, off-white #F4EFE6 (3).
- Audit: 100/100, no issues.
- PNG sizes: 512 px 2.6 KiB, 64 px 0.4 KiB, 32 px 0.3 KiB.
- 16/32 px: 32 px good (bubble, red line and tail visible); 16 px reads as a blue chat bubble with a red stripe, bars start to merge but the silhouette and red hold.
- Risks: generic "chat" silhouette (many chat apps); red/white/blue stripes can look like a flag; "blocked line" meaning needs a moment to be understood.

## B - M Block
- Idea: an M built from two blue pillars and a red wedge: stable structure, one sharp edge.
- Rationale: ownable initial, strongest brand-name recognition, highly geometric, scales to any size.
- Mark type: letterform/monogram.
- Colours: blue, red, off-white (3).
- Audit: 100/100, no issues.
- PNG sizes: 512 px 4.1 KiB, 64 px 0.6 KiB, 32 px 0.4 KiB.
- 16/32 px: best silhouette of the three; M reads at 32 px, at 16 px as two blue blocks with a red notch.
- Risks: says nothing about chat or moderation; the heavy flat M is close to a bold stock M (letter test passes but ownable twist is mild); red wedge can read as a pennant or a "V".

## C - Shield Chat
- Idea: the familiar red shield rebuilt in flat geometry around a typing bubble.
- Rationale: continuity with the current icon and instantly "protection plus chat"; the typing dots add warmth.
- Mark type: emblem/pictorial (containment).
- Colours: red #D6352B, black #111111, off-white (3).
- Audit: 100/100 (after snapping the tail angle to 60 degrees).
- PNG sizes: 512 px 12.0 KiB, 64 px 1.5 KiB, 32 px 0.8 KiB.
- 16/32 px: 32 px fine; at 16 px the dots shrink to a smudge but the red shield with a white bubble holds.
- Risks: shield is the category cliché (moderation/anticheat plugins all use it); least distinctive; red as a dominant colour suggests "alert/error".

## Recommendation
A - Bubble Gate: the clearest single idea, distinct from shield-heavy competitors, calm and Bauhaus-native, and it keeps the speech-bubble equity of the current icon. If a name-led mark is preferred, B is the strong second and could pair with A as a wordmark initial. All three are well under the 256 KiB limit. Trademark clearance is not checked.

## Files
a-bubble-gate / b-m-block / c-shield-chat: .svg, -512.png, -64.png, -32.png; concepts.png (overview, greyscale, A recommended); test-sheet.html (preview_sheet.py output, not screenshotted).

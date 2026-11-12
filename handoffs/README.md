# handoffs - the plan for the next feature round, split into 5 sessions

Each file is a ready-to-paste prompt for one fresh Claude Code session. Every prompt starts with "read `00-shared-context.md`", so
make sure the checkout is up to date (`git pull` on `main`) before pasting.

| File | Difficulty | What it builds | Suggested model |
|------|-----------|----------------|-----------------|
| [00-shared-context.md](00-shared-context.md) | read first | rules, build/test notes, the contracts the sessions share | - |
| [session-1-easy.md](session-1-easy.md) | easy | word lists for 35 languages (37 files) + validator | fast, cheap model |
| [session-2-medium.md](session-2-medium.md) | medium | shared engine, folder config, settings registry, migration, world profiles, `/abw whitelist` | mid-tier model |
| [session-3-medium.md](session-3-medium.md) | medium | spam control, look-alike/invisible-character detection, link filter, more scanned sources | mid-tier model |
| [session-4-hard.md](session-4-hard.md) | hard | persistent strikes, escalation ladder, mute, history | strongest model |
| [session-5-hard.md](session-5-hard.md) | hard | Discord (webhook + DiscordSRV) and the in-game settings GUI | strongest model |

## Order

1. Session 1 and session 2 can run **at the same time** (they touch different files).
2. Session 3 and session 4 start only **after session 2 is merged**. They can run at the same time; they both touch the adapters, so rebase often.
3. Session 5 starts last (after 2 and 4 are merged, 3 preferred). It is big: Part A (Discord) and Part B (GUI) can be run as two sessions.

Each session works on its own branch (`handoff/session-<n>-<name>`) and ends with a pull request to `main`.

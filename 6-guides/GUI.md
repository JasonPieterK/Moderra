# Settings menu (GUI) guide

*Verified: engine unit tests (clicks are simulated against the real menu logic). Bukkit chest code: compiled on GitHub against the real Bukkit API, never run on a server. Fabric/Forge chest code: compiled on GitHub, never run, and less complete (see the end).*

## The idea
Type `/abw` as a player who has the permission `antibadwords.gui` (operators have it). A chest opens. Every setting of every config file is a button. Click to change, click **Done** to save. Nothing is saved before Done. The console still gets the normal text help. `/abw gui` also opens it.

## Open a page directly
`/abw gui <page>` where page is: a number (main menu page), a category name (`spam`, `links`, `player names`; a unique start of the name is enough), `languages`, `words`, `ladder`, `mutes`, `profiles`, `rules`, `search <text>`, or a setting key.

## Menu map
* **Main menu**: one button per category, then the special pages and three info items (protection level, scan sources, active profile). Slot 47 = **Search settings**.
  * **Category page** (General, Languages, Scanning, Player names, Matching, Whitelist, Messages, Punishment, Strikes, Ladder, Worlds, Spam, Links, Evasion, Look-alike tables, History, Mute, Discord, Integrations): one button per setting.
  * **Language lists**: every word list with word counts and review status. Left click = on/off. Right click = browse its words (read-only).
  * **Word browser**: language lists (read-only), your custom list (add with a button, shift-click removes; applies at once) and the whitelist.
  * **Punishment ladder**: list of steps. Click a step to edit At, Repeat, Message, Broadcast, Final command and its actions. Shift-right-click deletes. "Add step" asks for a name.
  * **Muted players**: list with time left, reason and who muted. Click unmutes (needs `antibadwords.mute`; applies at once).
  * **World profiles**: add, delete, edit overrides (`<key> <value>`).
  * **World rules**: pattern to profile lines. Left/right click changes the profile, shift-click moves the line, middle click removes.
* Bottom row of every page: back, previous page, add, cancel, page/unsaved info, Done, reset list, next page, main menu.

## Clicks
| Setting type | Left click | Right click | Shift |
|---|---|---|---|
| on/off | toggle | toggle | |
| number, time | up one step | down one step | x10 |
| choice list | next | previous | |
| text | asks you in chat | asks you in chat | |
| list of texts | opens the list: click an item removes it, "Add" asks for a new one | | |
| any setting | middle click or drop key = back to the default | | |

The title gets a `*` while you have unsaved changes. Closing with changes opens a page: Save, Discard or Keep editing. Done checks every value first; if one is wrong, nothing is saved and you see why.
Comments inside a ladder or profile section are lost when you save that section from the menu (the rest of the file stays as it was).

## Text prompts
Settings that need text (a message, a webhook URL, a new list item) ask you in **chat**: the menu closes, you type the answer, the menu opens again. The answer is not shown to other players, not scanned, not counted as a violation. Type `cancel` (setting `gui.prompt.cancel-word`) to stop. It stops by itself after `gui.prompt.timeout` (60s). Secret values (webhook URLs) are never shown in the menu or echoed back.
`gui.prompt.mode` can be `auto`, `sign` or `chat`; right now every server type uses chat (a sign-editor prompt is not done).

## Permissions
| Node | Default | Meaning |
|---|---|---|
| `antibadwords.gui` | op | open the menu |
| `antibadwords.gui.edit.<category>` | **true** | change settings of that category |

The category name is lower case with other characters turned into `-`: `antibadwords.gui.edit.spam`, `.links`, `.player-names`, `.look-alike-tables`, `.ladder`, `.worlds`, `.languages`, `.discord`, `.integrations`, `.mute`, `.history` ...
Everyone who may open the menu may edit everything, until you take a node away (for example `/lp group helper permission set antibadwords.gui.edit.discord false`). Viewing is always allowed; the lore says when a setting is locked. Rights are checked on every click and again at Done. Wildcards (`antibadwords.gui.edit.*`) are handled by your permission plugin. Changing settings in the menu is as powerful as editing the files, so give `antibadwords.gui` only to people you trust.

## Icons (Bukkit)
`gui-icons.yml` in the plugin folder maps icon names to item names. Change one line to change an icon; unknown names show paper.

## Fabric and Forge: differences
* The chest shows **names only: no lore (description lines) yet**. The text commands and the files stay the full way to see and change everything.
* Prompts are **chat only** (no sign editor).
* Fewer icon kinds (paper, book, barrier, arrow, name tag, writable book).
* Click types are mapped per Minecraft version (1.21.11 and 26.2 and older use one API, 26.3 another). Never run on a real server.
* Edit nodes: everybody who may open the menu may edit, until a permission mod (LuckPerms) says no. Forge does not list the edit nodes in its own permission list; the fallback applies.

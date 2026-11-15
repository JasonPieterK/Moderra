# Settings menu (GUI) guide

*Verified: engine unit tests (clicks are simulated against the real menu logic). Bukkit, Fabric and Forge chest code: compiled against the real APIs, never run on a server.*

## The idea
Type `/moderra` as a player who has the permission `moderra.gui` (operators have it). A chest opens. Every setting of every config file is a button. Click to change, click **Done** to save. Nothing is saved before Done. The console still gets the normal text help. `/moderra gui` also opens it.

## Open a page directly
`/moderra gui <page>` where page is: a number (main menu page), a category name (`spam`, `links`, `player names`; a unique start of the name is enough), `languages`, `words`, `ladder`, `mutes`, `profiles`, `rules`, `search <text>`, or a setting key.

## Menu map
* **Main menu**: one button per category, then the special pages and three info items (protection level, scan sources, active profile). Slot 47 = **Search settings**.
  * **Category page** (General, Languages, Scanning, Player names, Matching, Whitelist, Messages, Punishment, Strikes, Ladder, Worlds, Spam, Links, Evasion, Look-alike tables, History, Mute, Discord, Integrations): one button per setting.
  * **Language lists**: every word list with word counts and review status. Left click = on/off. Right click = browse its words (read-only).
  * **Word browser**: language lists (read-only), your custom list (add with a button, shift-click removes; applies at once) and the whitelist.
  * **Punishment ladder**: list of steps. Click a step to edit At, Repeat, Message, Broadcast, Final command and its actions. Shift-right-click deletes. "Add step" asks for a name.
  * **Muted players**: list with time left, reason and who muted. Click unmutes (needs `moderra.mute`; applies at once).
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
Settings that need text (a message, a webhook URL, a new list item) ask you for the value. The answer is not shown to other players, not scanned, not counted as a violation. The prompt stops by itself after `gui.prompt.timeout` (60s). Secret values (webhook URLs) are never shown in the menu or echoed back. If the value is not valid, the error text tells you why and you can try again.
`gui.prompt.mode` chooses how you type:
| Mode | What happens | Where |
|---|---|---|
| `chat` | The menu closes, you type the answer in chat, the menu opens again. Type `cancel` (setting `gui.prompt.cancel-word`) to stop. | all server types; the only way on Bukkit |
| `sign` | A sign editor opens. Only you see the fake sign; the real block is restored. The lines are joined with spaces. An empty sign cancels. | Fabric and Forge, all six Minecraft versions |
| `anvil` | A rename screen opens. Type in the name field and click the result item. No item is given and no XP is used. | Fabric and Forge |
| `auto` (default) | `sign` where the server software supports it, otherwise `chat`. A mode that is not available uses the chat. | |
The sign or anvil input is removed from the screen when the time limit ends, when you cancel and when you leave the server.
The texts of the prompt are settings (`gui.prompt.messages.*`, formal style, `{title}`, `{hint}`, `{seconds}`, `{cancel}`, `{error}`).

## How the texts look
Item names are gold, the lines below are gray. Results of a click (saved, error, discarded) come in chat with the standard prefix: `[Moderra] Done. The plugin saved 2 settings and reloaded the configuration.` Titles stay short because old Bukkit versions allow only 32 characters.

## Permissions
| Node | Default | Meaning |
|---|---|---|
| `moderra.gui` | op | open the menu |
| `moderra.gui.edit.<category>` | **true** | change settings of that category |

The category name is lower case with other characters turned into `-`: `moderra.gui.edit.spam`, `.links`, `.player-names`, `.look-alike-tables`, `.ladder`, `.worlds`, `.languages`, `.discord`, `.integrations`, `.mute`, `.history` ...
Everyone who may open the menu may edit everything, until you take a node away (for example `/lp group helper permission set moderra.gui.edit.discord false`). Viewing is always allowed; the lore says when a setting is locked. Rights are checked on every click and again at Done. Wildcards (`moderra.gui.edit.*`) are handled by your permission plugin. Changing settings in the menu is as powerful as editing the files, so give `moderra.gui` only to people you trust.

## Icons (Bukkit)
`gui-icons.yml` in the plugin folder maps icon names to item names. Change one line to change an icon; unknown names show paper.

## Fabric and Forge: differences
* The items show their help as lore, as on Bukkit (`DataComponents.LORE`, the same call on all six Minecraft versions).
* Text input uses a fake sign by default (see the table above). Bukkit has no sign prompt (Paper-only API); it uses the chat.
* Fewer icon kinds (paper, book, barrier, arrow, name tag, writable book).
* Click types are mapped per Minecraft version (1.21.11 uses `ClickType`, the 26.x versions use `ContainerInput`). Never run on a real server.
* Edit nodes: everybody who may open the menu may edit, until a permission mod (LuckPerms) says no. Forge does not list the edit nodes in its own permission list; the fallback applies.

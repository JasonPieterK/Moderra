## Task 49 (Bukkit integrations, API names)
- plugin-apis job 132743385683: the only FAIL was `placeholderapi-fabric ... registerCommon(Identifier,Handler)` (a mod entry, task 51). No FAIL belongs to the Bukkit integration files.
- DiscordSrvHook: slash provider now registered with the real `ApiManager.addSlashCommandProvider(SlashCommandProvider)`, removed with `removeSlashCommandProvider`, then `updateSlashCommands()` (all seen in the dump). Name-guessing loops removed. Reply path keeps reflection (shaded JDA is not a dependency): `Interaction.reply(String)` -> `ReplyAction.setEphemeral(boolean)` -> `RestAction.queue()`; `getUser/getMember/getOptions/getChannel` are real.
- mcMMO: sender UUID via `Identity.uuid()` (real, abstract, in the dump); display-name lookup stays as fallback.
- Manifest: appended 8 entries (ApiManager add/remove/update, Interaction getUser/getMember/reply, RestAction queue, Identity uuid). Not run in CI yet.
- Verification: stub compile of changed files showed no errors in them (stub lacks org.bukkit.event.Event, unrelated). Engine tests not affected.

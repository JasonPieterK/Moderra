# live-player-test - a robot player that swears

`bot.js` joins a test server (offline mode) as "Tester" and checks, with 15 checks, that AntiBadWords reacts correctly to:
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
tries `/abw lang list`, `/abw lang enable de` / `disable de` and `/abw whitelist add` / `list` / `remove` (7 more checks).
A world-profile test is not in the bot (it would need two worlds and a prepared `worlds.yml`); profile rules are covered
by the engine's unit tests.

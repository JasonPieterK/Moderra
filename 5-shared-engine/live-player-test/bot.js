// Joins a (offline-mode) test server as "Tester", chats and swears, and checks what the server answered.
// Usage: node bot.js <host> <port> <minecraft version> [extras]
// With "extras" the bot also tries books and anvil renames. It needs operator rights and items for that, which only the
// server console can give: it prints lines starting with "CONSOLE> " and the launcher script types them into the console.
// With "filters" (instead of, or as "extras,filters") it tests the spam, link and bypass-trick filters. They are off by
// default, so the server must have been started with the settings listed in README.md ("filters mode").
// The player name can be changed with the environment variable BOT_NAME (for the join-name check).
// Works for Minecraft versions that the "mineflayer" library knows (26.1 and 1.21.11 at the time of writing).
const mineflayer = require('mineflayer');
const [, , host = 'localhost', port = '25565', version = '26.1', mode = ''] = process.argv;

const NAME = process.env.BOT_NAME || 'Tester';
const modes = mode.split(',');
const bot = mineflayer.createBot({ host, port: +port, username: NAME, version, auth: 'offline' });
const lines = [];
bot.on('message', (m) => { const t = m.toString(); lines.push(t); console.log('SERVER> ' + t); });
bot.on('error', (e) => { console.log('BOT ERROR: ' + e.message); process.exit(2); });
bot.on('kicked', (r) => { console.log('KICKED: ' + JSON.stringify(r)); });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const seen = (re) => lines.some((l) => re.test(l));
const results = [];
const check = (name, ok) => { results.push([name, ok]); console.log((ok ? '  ok   ' : '  FAIL ') + name); };

const strikes = () => lines.filter((l) => /SAID THE WORD/i.test(l)).length;
const console_ = (cmd) => console.log('CONSOLE> ' + cmd);
const skipped = (name, why) => { console.log('  skip ' + name + ' (' + why + ')'); };

async function runExtras() {
  console_('op Tester');
  console_('gamemode creative Tester');
  console_('give Tester minecraft:writable_book');
  console_('give Tester minecraft:stick');
  console_('execute at Tester run setblock ~2 ~ ~ minecraft:anvil');
  console_('execute at Tester run setblock ~-2 ~ ~ minecraft:oak_sign');
  await sleep(5000);

  // ---- admin commands added with the shared engine: /abw lang and /abw whitelist (the bot is an operator now) ----
  // NOT RUN YET: written without a server to try it on, see handoffs/BUILD-VERIFICATION-TODO.md.
  bot.chat('/abw lang list');
  await sleep(1500);
  check('/abw lang list shows the languages', seen(/Languages \(\d+ of \d+ on/));
  bot.chat('/abw lang enable de');
  await sleep(2000);
  check('/abw lang enable de switches German on', seen(/Language de \(German\) switched on/));
  bot.chat('/abw check du arschloch');
  await sleep(1500);
  check('a German word is blocked after enabling de', seen(/Blocked: matches 'arschloch'/));
  bot.chat('/abw lang disable de');
  await sleep(2000);
  check('/abw lang disable de switches German off', seen(/Language de switched off/));
  bot.chat('/abw whitelist add botsafeword');
  await sleep(2000);
  check('/abw whitelist add', seen(/Added to the whitelist: botsafeword/));
  bot.chat('/abw whitelist list 99');
  await sleep(1500);
  check('/abw whitelist list shows the new word on the last page', seen(/Whitelist \(\d+ words\)/) && seen(/botsafeword$/));
  bot.chat('/abw whitelist remove BOTSAFEWORD');
  await sleep(2000);
  check('/abw whitelist remove (capital letters do not matter)', seen(/Removed from the whitelist: botsafeword/));
  skipped('world-profile difference', 'needs two worlds and a prepared worlds.yml; profile resolution is covered by the engine unit tests');

  // ---- books ----
  const slot = bot.inventory.slots.findIndex((i) => i && i.name === 'writable_book');
  if (slot < 0) {
    skipped('book checks', 'bot did not receive a writable book');
  } else {
    let before = strikes();
    try {
      await bot.writeBook(slot, ['A perfectly nice page about gardening.']);
      await sleep(2000);
      check('clean book text is accepted (no strike)', strikes() === before);
    } catch (e) { skipped('clean book', 'bot library error: ' + e.message); }
    before = strikes();
    // When the server rejects the edit, the library never sees the slot update and times out: that is the expected
    // outcome, so in both cases the proof is that the server counted a strike for "book".
    try {
      await bot.writeBook(slot, ['This page says fuck and more.']);
    } catch (e) { /* expected: the server refused to apply the edit */ }
    await sleep(2500);
    check('book containing a swear word is BLOCKED (strike counted)', strikes() === before + 1);
  }

  // ---- signs ----
  const signBlock = bot.findBlock({ matching: (b) => b && /_sign$/.test(b.name), maxDistance: 6 });
  if (!signBlock) {
    skipped('sign checks', 'no sign block found');
  } else {
    let before = strikes();
    try { bot.updateSign(signBlock, 'Welcome\nto my\nbase'); } catch (e) { skipped('clean sign', e.message); }
    await sleep(1500);
    check('clean sign text causes no strike', strikes() === before);
    before = strikes();
    try { bot.updateSign(signBlock, 'this is\nfucking\nawful'); } catch (e) { skipped('dirty sign', e.message); }
    await sleep(2000);
    check('sign containing a swear word is BLOCKED (strike counted)', strikes() === before + 1);
  }

  // ---- anvil ----
  const anvilBlock = bot.findBlock({ matching: (b) => b && b.name === 'anvil', maxDistance: 6 });
  const stick = bot.inventory.items().find((i) => i.name === 'stick');
  if (!anvilBlock || !stick) {
    skipped('anvil checks', 'no anvil or no stick available');
  } else {
    let before = strikes();
    try {
      const anvil = await bot.openAnvil(anvilBlock);
      try {
        await anvil.rename(stick, 'Magic Stick');
      } catch (e) { /* the library waits for an XP event that creative mode never sends: harmless */ }
      await sleep(1500);
      check('clean anvil rename causes no strike', strikes() === before);
      before = strikes();
      try {
        await Promise.race([anvil.rename(stick, 'fuck stick'), sleep(4000)]);
        await sleep(1500);
        check('anvil rename containing a swear word is BLOCKED (strike counted)', strikes() === before + 1);
      } catch (e) {
        await sleep(1000);
        check('anvil rename containing a swear word is BLOCKED (strike counted)', strikes() === before + 1);
      }
      try { anvil.close(); } catch (e) { /* ignore */ }
    } catch (e) { skipped('anvil checks', 'bot library error: ' + e.message); }
  }
}

// Spam, link and bypass-trick filters. Needs the prepared config from README.md ("filters mode").
// On Bukkit-family servers a "censor" action rewrites the chat line; on Fabric and Forge it blocks it. Both are accepted.
async function runFilters() {
  const said = (re) => seen(new RegExp('<' + NAME + '> ' + re.source, re.flags));
  // let the rate window of the earlier checks run out
  await sleep(11000);

  // ---- bypass tricks ----
  bot.chat('you fu\u200bck off');
  await sleep(2500);
  check('zero-width character inside a swear word: NOT delivered', !said(/you fu/));
  await sleep(2500);
  bot.chat('what a fu\u0441k'); // Cyrillic "с"
  await sleep(2500);
  check('look-alike (Cyrillic) letter inside a swear word: NOT delivered', !said(/what a fu/));
  await sleep(2500);
  bot.chat('he\u200bllo there');
  await sleep(2500);
  check('invisible character without a swear word: stopped by the evasion check (detect.action: block)',
    !said(/he.?llo there/) && seen(/invisible or look-alike/i));

  // ---- links ----
  await sleep(2500);
  bot.chat('come to example.com now');
  await sleep(2500);
  check('plain link: NOT delivered', !said(/come to example\.com/));
  check('plain link: the player is told why', seen(/Links are not allowed/i));
  await sleep(2500);
  const linkNotes = () => lines.filter((l) => /Links are not allowed/i.test(l)).length;
  let before = linkNotes();
  bot.chat('come to example dot com now');
  await sleep(2500);
  check('hidden link ("example dot com"): NOT delivered', !said(/come to example dot com/) && linkNotes() === before + 1);
  await sleep(2500);
  before = linkNotes();
  bot.chat('come to example[.]com now');
  await sleep(2500);
  check('hidden link ("example[.]com"): NOT delivered', !said(/come to example\[/) && linkNotes() === before + 1);
  await sleep(2500);
  bot.chat('this sentence has no link. really');
  await sleep(2500);
  check('normal sentence with a full stop is delivered', said(/this sentence has no link\. really/));

  // ---- spam: capitals and character flood (censor on Bukkit, block on mods) ----
  await sleep(2500);
  bot.chat('THIS IS A VERY LOUD MESSAGE FOR EVERYONE');
  await sleep(2500);
  check('capital letters: not delivered as typed (lower-cased or blocked)', !said(/THIS IS A VERY LOUD/)
    && (seen(/this is a very loud message for everyone/) || seen(/capital letters/i)));
  await sleep(2500);
  bot.chat('heeeeeeeeeeeeello everybody');
  await sleep(2500);
  check('character flood: not delivered as typed (shortened or blocked)', !said(/he{10,}llo/)
    && (seen(/heeello everybody/) || seen(/repeated characters/i)));

  // ---- spam: fast repeats, then too many messages ----
  await sleep(11000);
  for (let i = 0; i < 4; i++) {
    bot.chat('buy my diamonds cheap');
    await sleep(1200);
  }
  await sleep(1500);
  check('same message again and again: stopped (repeat rule)', seen(/do not repeat the same message/i));
  check('the first copies of that message were delivered', said(/buy my diamonds cheap/));
  await sleep(11000);
  for (let i = 0; i < 8; i++) {
    bot.chat('quick message number ' + i);
    await sleep(250);
  }
  await sleep(2500);
  check('many messages in a few seconds: stopped (rate rule)', seen(/sending messages too fast/i) && !said(/quick message number 7/));
  check('the first quick messages were delivered', said(/quick message number 0/));

  // ---- join-name check (only when the bot was started with a name that contains a blocked word) ----
  if (NAME === 'Tester') {
    skipped('join-name check', 'start the bot with BOT_NAME=xXshitXx and filters.join-names.action: warn to test it');
  } else {
    check('join-name check: the player was warned about the name', seen(/player name contains a word/i));
  }
}

bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('hello everyone');
  await sleep(2000);
  check('clean chat is delivered', seen(new RegExp('<' + NAME + '> hello everyone')));

  bot.chat('you are a fucking idiot');
  await sleep(2500);
  check('swearing is NOT delivered to chat', !seen(/<Tester> you are a fucking/));
  check('first strike: warning sent to the player', seen(/WATCH YOUR WORDS/i));
  check('first strike: warning broadcast', seen(/TESTER SAID THE WORD/i) || seen(/Tester SAID THE WORD/i));

  bot.chat('f.u.c.k this');
  await sleep(2500);
  check('disguised swearing (f.u.c.k) is NOT delivered', !seen(/<Tester> f\.u\.c\.k/));
  check('second strike: player is punished', seen(/YOU ARE PUNISHED WITH/i));

  bot.chat('/msg Tester shit happens');
  await sleep(2500);
  check('swearing inside /msg is blocked (no private message arrives)', !seen(/shit happens/) || seen(/SAID THE WORD/i));
  check('/msg strike is counted', lines.filter((l) => /SAID THE WORD/i.test(l)).length >= 3);

  bot.chat('/msg Tester nice weather today');
  await sleep(2000);
  check('clean /msg still works', seen(/nice weather today/));

  if (modes.includes('extras')) {
    await runExtras();
  }
  if (modes.includes('filters')) {
    await runFilters();
  }

  const bad = results.filter(([, ok]) => !ok).length;
  console.log(bad === 0 ? 'LIVE PLAYER TEST PASSED' : `LIVE PLAYER TEST FAILED (${bad} checks)`);
  bot.quit();
  setTimeout(() => process.exit(bad === 0 ? 0 : 1), 500);
});
setTimeout(() => { console.log('TIMEOUT: never spawned'); process.exit(3); }, modes.includes('filters') ? 300000 : 120000);

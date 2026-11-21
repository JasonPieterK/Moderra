#!/usr/bin/env python3
"""Writes the HTML sources of the nine Bauhaus graphics (run: python3 gen.py, then node render.js)."""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
R, B, Y, K, P = "#D6352B", "#1E4FA3", "#F2C230", "#111111", "#F4EFE6"
COL = {"red": R, "blue": B, "yellow": Y, "black": K, "paper": P}


def on(c):  # readable text colour on a background
    return K if c in (Y, P) else P


def shape(kind, fill, size=84, stroke=K, sw=6):
    body = {"c": '<circle cx="50" cy="50" r="42"/>',
            "s": '<rect x="9" y="9" width="82" height="82"/>',
            "t": '<polygon points="50,6 95,92 5,92"/>'}[kind]
    return (f'<svg viewBox="0 0 100 100" width="{size}" height="{size}" '
            f'style="fill:{fill};stroke:{stroke};stroke-width:{sw};display:block">{body}</svg>')


PH_DIR = os.path.join(HERE, "phosphor")


def ph(name, color=K, size=64, extra="", weight="bold"):
    """Inline Phosphor Icons (MIT, vendored in ./phosphor): offline-safe SVG, Bold weight everywhere (Fill only for check-circle)."""
    s = open(os.path.join(PH_DIR, f"{name}-{weight}.svg")).read()
    inner = s[s.index(">") + 1:s.rindex("</svg>")]
    return f'<svg class="ph" viewBox="0 0 256 256" width="{size}" height="{size}" fill="{color}" {extra}>{inner}</svg>'


def page(name, css, body):
    with open(os.path.join(HERE, name), "w") as f:
        f.write(f'<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="common.css">'
                f'<style>{css}</style></head><body>{body}</body></html>')


def head(title, sub, num):
    return (f'<div class="head"><div class="t">{title}</div><div class="s">{sub}</div>'
            f'<div class="n">{num}</div></div>')


# ---------------------------------------------------------------- 01 hero
page("01-hero.html", """
.frame{height:640px}
.title{position:absolute;left:56px;top:56px;font-size:122px;line-height:.95;text-transform:uppercase;letter-spacing:-.01em}
.title span{display:block}
.two{position:absolute;left:56px;top:350px;width:150px;height:150px;border-radius:50%;background:#D6352B;color:#F4EFE6;font-size:58px;display:flex;align-items:center;justify-content:center;border:10px solid #111}
.tag{position:absolute;left:236px;top:372px;font-size:32px;line-height:1.35;text-transform:uppercase;width:720px}
.tag b{background:#F2C230;padding:0 8px}
.bigc{position:absolute;right:120px;top:36px;width:470px;height:470px;border:12px solid #111;background:#0A1124;overflow:hidden}.bigc img{display:block;width:100%;height:100%}
.sq{position:absolute;right:330px;top:250px;width:280px;height:280px;background:#1E4FA3;border:12px solid #111}
.trg{position:absolute;right:110px;top:290px;--s:110px}
.vr{position:absolute;right:560px;top:0;width:12px;height:520px;background:#111}
.bar{position:absolute;left:0;right:0;bottom:0;height:100px;background:#111;color:#F4EFE6;display:flex;align-items:center;padding:0 56px;font-size:29px;text-transform:uppercase;gap:26px;white-space:nowrap}
.bar span{display:flex;align-items:center;gap:14px}.bar svg{flex:none;display:block}.bar .sep{width:6px;height:44px;background:#F4EFE6;opacity:.35}
""", '''<div class="frame"><div class="bigc"><img src="../../../branding/logo-a-refined/moderra-icon.svg" alt=""></div>
<div class="title"><span>Moderra</span></div><div class="two">1.0</div>
<div class="tag">Keep your chat clean.<br><b>Everywhere</b> players can type.</div>
<div class="bar"><span>'''+ph("funnel",P,44)+'''Chat filter</span><span class="sep"></span><span>'''+ph("shield-check",P,44)+'''Moderation</span><span class="sep"></span><span>'''+ph("hard-drives",P,44)+'''6 server types</span><span class="sep"></span><span>'''+ph("stack",P,44)+'''6 Minecraft versions</span></div></div>''')

# ---------------------------------------------------------------- 02 where
items = [("Chat", "Public chat", R, "chat-circle-text"), ("Commands", "/msg /tell /me /say /mail /nick and more", B, "terminal-window"),
         ("Signs", "Front side and back side", Y, "signpost"), ("Books", "Every page and the title", R, "book-open"),
         ("Anvils", "The text of an item rename", B, "hammer"), ("Join names", "Player names, when they join", Y, "user-plus"),
         ("Command blocks", "A blocked command does not run", P, "code-block"),
         ("Discord", "Messages typed in Discord (DiscordSRV)", R, "discord-logo"),
         ("Outgoing text", "System chat, team and tab list (ProtocolLib or PacketEvents, optional)", B, "broadcast")]
tiles = ""
for i, (t, d, c, sh) in enumerate(items):
    ic = on(c)
    tiles += (f'<div class="tile" style="background:{c};color:{on(c)}">'
              f'{ph(sh, ic, 84)}<div class="n">0{i + 1}</div><h3>{t}</h3><p>{d}</p></div>')
page("02-where.html", """
.grid{display:grid;grid-template-columns:repeat(3,1fr)}
.tile{height:290px;padding:34px 40px;border-right:12px solid #111;border-bottom:12px solid #111;position:relative}
.tile:nth-child(3n){border-right:0}.tile:nth-child(n+7){border-bottom:0}
.tile svg{margin-bottom:20px}.tile .n{position:absolute;right:36px;top:28px;font-size:64px}
.tile h3{font-size:38px;margin-bottom:10px}.tile p{font-size:23px;line-height:1.25}
""", '<div class="frame">' + head("Where it checks", "Nine places where text enters your server", "9") + f'<div class="grid">{tiles}</div></div>')

# ---------------------------------------------------------------- 03 pipeline
SICON = {"1": "eye-slash", "2": "prohibit", "3": "link-break", "4": "repeat"}
ARROW = ph("arrow-right", K, 60, 'class="ar"')
stages = [("1", "Evasion", "Invisible characters, look-alike letters, leetspeak, f*ck", "filters/evasion.yml", "off until you switch it on", B),
          ("2", "Bad words", "37 word lists, 3 strength levels, whitelist, your own words", "wordlists/*.yml", "English + custom on", R),
          ("3", "Links", "Links, hidden links, IP addresses, invites", "filters/links.yml", "off until you pick a mode", Y),
          ("4", "Spam", "Rate, repeat, caps, flood, words, commands", "filters/spam.yml", "off until you switch it on", K)]
cards = ""
for i, (n, t, d, f, s, c) in enumerate(stages):
    cards += (f'<div class="st" style="background:{c};color:{on(c)}"><div class="num">{n}</div><div class="sic">{ph(SICON[n], on(c), 64)}</div><h3>{t}</h3><p>{d}</p>'
              f'<div class="meta"><span>{f}</span><span>{s}</span></div></div>')
    if i < 3:
        cards += ARROW
page("03-pipeline.html", """
.flow{display:flex;align-items:center;padding:50px 30px 30px 30px}
.in{width:150px;height:300px;background:#111;color:#F4EFE6;display:flex;flex-direction:column;justify-content:center;align-items:center;font-size:26px;text-transform:uppercase;text-align:center;gap:14px;flex:none}
.st .sic{position:absolute;right:20px;top:22px}.out b svg{vertical-align:-6px;margin-right:12px}
.st{width:280px;height:420px;border:12px solid #111;padding:22px 22px;flex:none;position:relative}
.st .num{font-size:90px;line-height:.9}.st h3{font-size:30px;margin:6px 0 12px}.st p{font-size:21px;line-height:1.25}
.st .meta{position:absolute;left:22px;right:22px;bottom:18px;font-size:16px;line-height:1.3;display:flex;flex-direction:column;gap:3px;text-transform:uppercase}
.ar{flex:none;margin:0 -2px}
.out{display:flex;border-top:12px solid #111}
.out div{flex:1;padding:26px 40px;font-size:24px;line-height:1.3;border-right:12px solid #111}
.out div:last-child{border-right:0}.out b{display:block;font-size:40px;margin-bottom:6px;text-transform:uppercase}
""", '<div class="frame">' + head("Filter pipeline", "A message passes the checks in order", "4") +
     f'<div class="flow"><div class="in">{ph("chat-circle-text", P, 80)}Message</div>{ARROW}{cards}</div>'
     '<div class="out"><div style="background:#F4EFE6"><b>'+ph("check-circle",K,40)+'Passes</b>No check fires. Everyone sees the message.</div>'
     '<div style="background:#F2C230"><b>'+ph("funnel",K,40)+'Cleaned</b>A check censors the text. The next check reads the cleaned text.</div>'
     '<div style="background:#D6352B;color:#F4EFE6"><b>'+ph("x-circle",P,40)+'Blocked</b>The first check that blocks ends the search. The player gets a strike.</div></div>'
     '<div class="note">You can change the order with <span style="color:#F2C230">filters.check-order</span>. Default: evasion, badword, link, spam.</div></div>')

# ---------------------------------------------------------------- 04 ladder
steps = [("2", "Warn", "Watch your language.", Y, "warning"), ("4", "Mute 10m", "No chat for 10 minutes.", B, "speaker-x"),
         ("6", "Kick", "Removed from the server.", R, "sign-out"), ("8", "Tempban 7d", "Needs a ban plugin, or vanilla ban.", K, "clock-countdown")]
st = ""
for i, (n, a, d, c, ic) in enumerate(steps):
    h = 240 + i * 110
    st += (f'<div class="step" style="left:{i * 372}px;height:{h}px;background:{c};color:{on(c)}"><div class="at">AT <b>{n}</b> STRIKES</div><div class="sic">{ph(ic, on(c), 72)}</div>'
           f'<h3>{a}</h3><p>{d}</p></div>')
page("04-ladder.html", """
.stairs{position:relative;height:660px;margin:0 40px 40px}
.step{position:absolute;bottom:0;width:372px;border:12px solid #111;padding:20px 24px;margin-left:-0px}
.step .at{font-size:22px}.step .at b{font-size:54px;vertical-align:-6px}
.step .sic{position:absolute;right:22px;top:18px}.step h3{font-size:40px;margin:6px 0}.step p{font-size:21px;line-height:1.25}
.man{position:absolute;left:1180px;bottom:672px}
.cap{position:absolute;left:0;top:14px;font-size:30px;line-height:1.3;text-transform:uppercase;width:640px}
.cap span{background:#F2C230;padding:0 8px}
""", '<div class="frame">' + head("Escalation ladder", "Example ladder. You write your own.", "4") +
     f'<div class="stairs"><div class="cap">Every blocked message is a <span>strike</span>.<br>Strikes fade with time.<br>Each step runs once, or again every N strikes.</div>{st}</div>'
     '<div class="note">Actions: warn, mute, kick, ban, tempban, ipban, command, effect, sound. File: moderation/ladder.yml</div></div>')

# ---------------------------------------------------------------- 05 matrix
rows = [("26.3", "Java 25"), ("26.2", "Java 25"), ("26.1.2", "Java 25"), ("26.1.1", "Java 25"), ("26.1", "Java 25"), ("1.21.11", "Java 21")]
loaders = [("Bukkit", R), ("Spigot", Y), ("Paper", B), ("Purpur", P), ("Fabric", Y), ("Forge", K)]
cells = '<div class="c h" style="background:#111;color:#F4EFE6">Minecraft</div>'
for n, c in loaders:
    cells += f'<div class="c h" style="background:{c};color:{on(c)}">{n}</div>'
for v, j in rows:
    cells += f'<div class="c v"><span>{v}</span><small>{j}</small></div>'
    for n, c in loaders:
        cells += f'<div class="c">{ph("check-circle", K, 58, "", "fill")}</div>'
page("05-matrix.html", """
.m{display:grid;grid-template-columns:330px repeat(6,1fr)}
.c{height:92px;border-right:12px solid #111;border-bottom:12px solid #111;display:flex;align-items:center;justify-content:center;font-size:26px;text-transform:uppercase;text-align:center;padding:0 14px;line-height:1.1}
.c:nth-child(7n){border-right:0}.c.h{height:100px}
.c.v{justify-content:flex-start;gap:20px;font-size:44px;padding-left:34px;background:#F4EFE6}.c.v small{white-space:nowrap;font-size:17px;background:#111;color:#F4EFE6;padding:4px 8px}
""", '<div class="frame">' + head("Platforms and versions", "36 jars: 6 server types times 6 versions", "36") + f'<div class="m">{cells}</div>'
     '<div class="note" style="border-top:0">File name: <span style="color:#F2C230;text-transform:none">Moderra26.3-1.0-fabric.jar</span><br>Players install nothing. Put the jar on the server only.</div></div>')

# ---------------------------------------------------------------- 06 languages
latin = [("en", "English"), ("en-gb", "English UK extras"), ("de", "German"), ("fr", "French"), ("es", "Spanish"), ("it", "Italian"),
         ("pt-br", "Portuguese BR"), ("pt-pt", "Portuguese PT"), ("nl", "Dutch"), ("sv", "Swedish"), ("no", "Norwegian"), ("da", "Danish"),
         ("fi", "Finnish"), ("pl", "Polish"), ("cs", "Czech"), ("hu", "Hungarian"), ("ro", "Romanian"), ("tr", "Turkish"),
         ("id", "Indonesian"), ("ms", "Malay"), ("fil", "Filipino"), ("vi", "Vietnamese"), ("sw", "Swahili")]
assert len(latin) == 23


def chips(lst, bg, fg):
    return "".join(f'<div class="chip" style="background:{bg};color:{fg}"><b>{c}</b><span>{n}</span></div>' for c, n in lst)


groups = [("Cyrillic", 2, [("ru", "Russian"), ("uk", "Ukrainian")], R),
          ("Arabic script", 3, [("ar", "Arabic"), ("fa", "Persian"), ("ur", "Urdu")], B),
          ("Han", 2, [("zh", "Chinese simplified"), ("zh-tw", "Chinese traditional")], Y),
          ("Other scripts", 7, [("el", "Greek"), ("he", "Hebrew"), ("hi", "Hindi"), ("bn", "Bengali"), ("ja", "Japanese"), ("ko", "Korean"), ("th", "Thai")], K)]
right = ""
for t, n, l, c in groups:
    right += f'<div class="grp" style="background:{c};color:{on(c)}"><h3>{ph("translate", on(c), 38, 'style="vertical-align:-7px;margin-right:12px"')}{t} <em>{n}</em></h3><div class="cl">{chips(l, P, K)}</div></div>'
page("06-languages.html", """
.body{display:flex}
.lat{width:860px;border-right:12px solid #111;background:#1E4FA3;color:#F4EFE6;padding:30px 36px}
.lat h3,.grp h3{font-size:34px;margin-bottom:18px}.lat h3 em,.grp h3 em{font-style:normal;float:right;font-size:52px;line-height:.8}
.cl{display:flex;flex-wrap:wrap;gap:10px}
.chip{border:5px solid #111;padding:8px 12px;font-size:20px;line-height:1.15;min-width:150px;color:#111}
.chip b{display:block;font-size:26px;text-transform:uppercase}.chip span{font-size:16px}
.rt{flex:1;display:flex;flex-direction:column}.grp{padding:24px 30px;border-bottom:12px solid #111}.grp:last-child{border-bottom:0}
.grp .chip{min-width:130px}
""", '<div class="frame">' + head("37 word lists", "35 languages grouped by script", "37") +
     f'<div class="body"><div class="lat"><h3>{ph("translate", P, 38, 'style="vertical-align:-7px;margin-right:12px"')}Latin script <em>23</em></h3><div class="cl">{chips(latin, P, K)}</div></div><div class="rt">{right}</div></div>'
     '<div class="note">3 strength levels per list. English + your custom list start on. Enable more: /moderra lang enable de<br>First drafts written by an AI. All marked unreviewed. Ask a native speaker to read your lists.</div></div>')

# ---------------------------------------------------------------- 07 menu map
cats = "General Languages Scanning Player-names Matching Whitelist Messages Punishment Strikes Ladder Worlds Spam Links Evasion Look-alikes History Mute Discord Integrations".split()
lv1 = [("Category pages", "19 categories. One button per setting.", Y, 330), ("Language lists", "On/off per list. Right click browses.", B, 104),
       ("Punishment ladder", "List of steps.", R, 104), ("Muted players", "Time left, reason, who.", B, 104),
       ("World profiles", "Add, delete, edit.", Y, 104), ("World rules", "Pattern to profile.", R, 104), ("Search settings", "Find any setting.", K, 104)]
L1ICON = ["squares-four", "translate", "list", "speaker-x", "globe", "funnel", "magnifying-glass"]
lv2 = [None, ("Word browser", "Your custom list, whitelist and read-only lists"), ("Step editor", "At, repeat, message, broadcast, final command, actions"),
       ("Unmute by click", "Needs moderra.mute"), ("Override editor", "key value pairs"), ("Line editor", "Shift-click moves, middle click removes"),
       ("Jump to the setting", "Name, key or text")]
y = 20
boxes = ""
lines = ""
GAP = 24
total = sum(h for *_, h in lv1) + GAP * 6 + 40
rooty = total // 2 - 60
for i, (t, d, c, h) in enumerate(lv1):
    boxes += (f'<div class="b" style="left:430px;top:{y}px;width:400px;height:{h}px;background:{c};color:{on(c)}">'
              f'<h3>{t}</h3><p>{d}</p></div>')
    cy = y + h // 2
    lines += f'<path d="M300 {rooty + 60} H365 V{cy} H430" />'
    lines += f'<path d="M830 {cy} H900" />'
    boxes += f'<div class="node" style="left:837px;top:{cy - 28}px">{ph(L1ICON[i], K, 36)}</div>'
    if i == 0:
        chipsh = "".join(f'<span>{c}</span>' for c in cats)
        boxes += (f'<div class="b" style="left:900px;top:{y}px;width:640px;height:{h}px;background:#F4EFE6"><h3>Pages of the categories</h3>'
                  f'<div class="cc">{chipsh}</div></div>')
    else:
        a, b = lv2[i]
        boxes += (f'<div class="b" style="left:900px;top:{y}px;width:640px;height:{h}px;background:#F4EFE6"><h3>{a}</h3><p>{b}</p></div>')
    y += h + GAP
boxes += f'<div class="node" style="left:306px;top:{rooty + 60 - 28}px">{ph("terminal-window", K, 36)}</div>'
boxes += (f'<div class="b" style="left:30px;top:{rooty}px;width:270px;height:120px;background:#111;color:#F4EFE6;display:flex;align-items:center;justify-content:center">'
          '<h3 style="font-size:46px">/moderra</h3></div>')
height = y + 130
page("07-menu.html", f"""
.map{{position:relative;height:{height}px}}
.b{{position:absolute;border:8px solid #111;padding:10px 22px}}
.b h3{{font-size:28px;margin-bottom:6px}}.b p{{font-size:20px;line-height:1.2}}
.cc{{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}}.cc span{{border:4px solid #111;padding:3px 9px;font-size:18px;background:#F2C230;text-transform:uppercase}}
.node{{position:absolute;width:56px;height:56px;background:#F4EFE6;border:6px solid #111;display:flex;align-items:center;justify-content:center}}
svg.l{{position:absolute;left:0;top:0}}svg.l path{{stroke:#111;stroke-width:8;fill:none}}
.bot{{position:absolute;left:0;right:0;bottom:0;height:100px;background:#111;color:#F4EFE6;padding:16px 40px;font-size:22px;text-transform:uppercase;line-height:1.35}}
.bot span{{color:#F2C230}}
""", '<div class="frame">' + head("In-game menu", "Type /moderra. Every setting is a chest button.", "19") +
     f'<div class="map"><svg class="l" width="1572" height="{height}">{lines}</svg>{boxes}'
     '<div class="bot">Bottom row of every page: <span>back, previous, add, cancel, Done, reset, next, main menu</span>.<br>Nothing is saved before you click Done. Permission: moderra.gui</div></div></div>')

# ---------------------------------------------------------------- 08 integrations
integ = [("EssentialsX", "Mute, nicknames, /msg", B, "gear-six"), ("LuckPerms", "Groups pick a rule profile", B, "users-three"),
         ("PlaceholderAPI", "Strikes and mute as placeholders", B, "hash"), ("Vault", "Group and prefix", B, "key"),
         ("Ban plugins", "tempban and ipban actions", R, "gavel"), ("Chat plugins", "Channel profiles", R, "chat-text"),
         ("DiscordSRV", "Alerts, filter, !moderra and slash commands", Y, "discord-logo"), ("Floodgate", "Bedrock player names", Y, "door-open"),
         ("BungeeCord / Velocity", "Share mutes and strikes", Y, "tree-structure"), ("ProtocolLib", "Packet scan: sign, book, anvil, outgoing text", K, "package"),
         ("PacketEvents", "Same packet scan as ProtocolLib", K, "plug"), ("CoreProtect", "Logs blocked sign and book text", K, "shield-check"),
         ("Plan", "Strike data in Plan", K, "chart-line-up"), ("mcMMO", "Party chat commands", P, "sword"),
         ("Towny", "Town and nation chat", P, "flag"), ("Citizens", "NPC names", P, "user-circle"),
         ("DecentHolograms", "Hologram lines", P, "note-pencil")]
cells = ""
for n, d, c, icn in integ:
    cells += (f'<div class="ic"><div class="ib" style="background:{c}">{ph(icn, on(c) if c != P else K, 60)}</div><h3>{n}</h3><p>{d}</p></div>')
cells += f'<div class="ic more">{ph("puzzle-piece", P, 72, "style=margin-bottom:12px")}<h3>And more</h3><p>Factions, TAB, NametagEdit</p></div>'
page("08-integrations.html", """
.g{display:grid;grid-template-columns:repeat(6,1fr)}
.ic{height:250px;padding:26px 22px;border-right:8px solid #111;border-bottom:8px solid #111;background:#F4EFE6}
.ic:nth-child(6n){border-right:0}.ic:nth-child(n+13){border-bottom:0}
.ib{width:96px;height:96px;border:6px solid #111;display:flex;align-items:center;justify-content:center}
.ic h3{font-size:22px;margin:16px 0 6px;line-height:1.1}.ic p{font-size:17px;line-height:1.25}
.ic.more{background:#D6352B;color:#F4EFE6;display:flex;flex-direction:column;justify-content:center}.ic.more h3{font-size:30px}
.key{display:flex;gap:30px;align-items:center}
""", '<div class="frame">' + head("Integrations", "All optional. Bukkit family only.", "18") + f'<div class="g">{cells}</div>'
     '<div class="note">Blue: permissions, placeholders. Red: bans, chat. Yellow: Discord, Bedrock, network. Black: packets, logs. White: game plugins.<br>'
     'Names checked against the real plugins. Not yet run with them. No plugin hooks on Fabric and Forge.</div></div>')

# ---------------------------------------------------------------- 09 divider
page("09-divider.html", """
.frame{height:132px;border-width:12px}
.a{position:absolute;left:0;right:0;top:50%;height:12px;background:#111;margin-top:-6px}
.c{position:absolute;top:22px;width:64px;height:64px;background:#F4EFE6;display:flex;align-items:center;justify-content:center}
""", '<div class="frame"><div class="a"></div>'
     f'<div class="c" style="left:140px">{ph("chat-circle-text", K, 64)}</div>'
     f'<div class="c" style="left:760px">{ph("shield-check", K, 64)}</div>'
     f'<div class="c" style="left:1340px">{ph("prohibit", K, 64)}</div>'
     '<div style="position:absolute;left:0;top:0;width:60px;height:100%;background:#F2C230;border-right:12px solid #111"></div></div>')

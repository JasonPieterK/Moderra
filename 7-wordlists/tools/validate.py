#!/usr/bin/env python3
"""Validate the Moderra word lists in 7-wordlists/.

Usage:  python3 7-wordlists/tools/validate.py [--quiet] [--strict] [folder]

Python 3 standard library only. The files use a small, restricted YAML format
(see 7-wordlists/README.md), which this script reads with a built-in reader.
If PyYAML happens to be installed, the result is also cross-checked against it.

Exit code 0 = no errors. Non-zero = at least one error (or, with --strict,
at least one unjustified collision).
"""
import os
import re
import sys
import unicodedata

REQUIRED = ["language", "name", "script", "review-status", "sources", "notes",
            "level-1", "level-2", "level-3"]
STATUSES = {"unreviewed", "community-reviewed", "native-reviewed"}
SCRIPTS = {"latin", "cyrillic", "arabic", "han", "hangul", "kana", "thai",
           "devanagari", "bengali", "greek", "hebrew"}
TIERS = ["level-1", "level-2", "level-3"]

# (first, last) code point ranges per script
RANGES = {
    "latin": [(0x41, 0x5A), (0x61, 0x7A), (0xC0, 0x24F), (0x1E00, 0x1EFF)],
    "cyrillic": [(0x400, 0x52F)],
    "arabic": [(0x600, 0x6FF), (0x750, 0x77F), (0xFB50, 0xFDFF), (0xFE70, 0xFEFF)],
    "han": [(0x4E00, 0x9FFF), (0x3400, 0x4DBF), (0x20000, 0x2A6DF), (0x3005, 0x3007)],
    "hangul": [(0xAC00, 0xD7AF), (0x1100, 0x11FF), (0x3130, 0x318F)],
    "kana": [(0x3040, 0x30FF), (0x31F0, 0x31FF), (0xFF66, 0xFF9F)],
    "thai": [(0xE00, 0xE7F)],
    "devanagari": [(0x900, 0x97F)],
    "bengali": [(0x980, 0x9FF)],
    "greek": [(0x370, 0x3FF), (0x1F00, 0x1FFF)],
    "hebrew": [(0x590, 0x5FF)],
}
# which scripts a file with the given `script:` value may contain (besides latin)
ALLOWED_WITH = {
    "latin": {"latin"}, "cyrillic": {"cyrillic"}, "arabic": {"arabic"},
    "han": {"han"}, "hangul": {"hangul"}, "kana": {"kana", "han"},
    "thai": {"thai"}, "devanagari": {"devanagari"}, "bengali": {"bengali"},
    "greek": {"greek"}, "hebrew": {"hebrew"},
}
TWO_CHAR_SCRIPTS = {"han", "hangul", "kana", "thai"}
# characters that are allowed inside a word besides letters
PUNCT_OK = set(" -'.’·ー")

# Common English words that must not be listed in another language unless the
# file's notes mention the word. (Hand-written; extend it when you find more.)
ENGLISH_COMMON = set("""
the and for are but not you all any can had her was one our out day get has him his how man new now old see two way who boy did its let put say she too use
about after again also back because been before being below between both came come could down each even find first from give good great have here home into just keep kind know last left life like line little long look made make many more most much must name need never next only open other over part people place right said same seem show side small some still such take tell than that their them then there these they thing think this those three through time under until upon very want well were what when where which while will with without word work would write year your
bad bag ball band bank bar base bat bay bed bee beer bell belt bench bike bill bird bit black blank blast block blood blow blue board boat body bomb bone book boot born boss bottle bowl box brain bread break brick bridge bring brush bug build bull burn bus bush butter button buy cab cake call camp cap car card care carry case cash cast cat catch cause cave cell chain chair chance chart chat check cheese chest chick chief child chip city class clay clean clear click clock close cloth cloud club coal coat code coin cold color cook cool copy corn cost cotton count court cover cow crab craft crash crazy cream crew crop cross crowd crown cry cup cut dark data date dead deal dear death deep deer desk dig dirt dish dock dog doll door dot double dragon draw dream dress drink drive drop drum dry duck dust duty ear earth east easy eat edge egg end enemy engine face fact fair fall farm fast fat fear feed feel few field fight file fill film fire fish fit flag flat flip floor flow fly fog food foot force forest fork form fort fox frame free fresh friend frog front fruit fuel full fun game gap gas gate gear gem ghost gift girl glass glove goal goat god gold golf grab grass gray green grid grip ground group grow guard guess guest guide gun gym hair half hall hammer hand hang happy hard hat hay head heart heat heavy hell help hero hide high hill hit hold hole hook hope horn horse hot hotel hour house human hunt hurt ice idea inch iron island item jar jet job join joke jump jungle junk key kick kid kill king kiss kit knee knife knock lab lack lady lake lamp land lane large late laugh law lawn lazy lead leaf leg lend less lesson level lid lift light lion lip list live load lock log long loop lord lose loss lot loud love low luck lunch mad mail main map mark market mask mass match mate meal mean meat meet melon menu mess metal mile milk mind mine mint miss mix mob model money monkey moon more mouse mouth move music nail near neck nest net nice night noon nose note nut oak ocean oil one orange order ore pack page pain pair pan paper park pass past path pay peace pen pet phone pick pie pig pile pill pilot pin pink pipe pit plan plane plant plate play plot plug plus pocket point pole pool poor port post pot pound power press price pride print prize pull pump punch pure push quick quiet race rack rain raw reach read real red rest rice rich ride ring rise river road rock roll roof room root rope rose round row rub rule run rush sad safe sail salt sand save scale school score screen sea seat seed sell send set shade shape share sharp sheep shell shift ship shirt shoe shoot shop short shot shout shut sick sign silk silver sing sink sit size skin sky sleep slide slow smell smile smoke snow soap sock soft soil song soon sort soul sound soup south space speed spell spend spot spring square staff stage stair stamp stand star start state stay steal steam steel step stick stone stop store storm story straw street strike strong sugar suit sun super sure swim switch table tail tank tap taste tax tea team test text thick thin ticket tide tie tiger tin tip tire toast toe tool tooth top touch tough tour tower town toy track trade trail train trap tree trick trip truck true trust try tube tune turn twin type uncle unit use van view village voice wait wake walk wall war warm wash waste watch water wave wax weak wear west wet wheel white wide wife wild win wind window wine wing winter wire wish wit wolf wood wool world worm worth wrap yard yellow zero zone
fart pic tit tits hoe bum butt pee poop sex gay ass arse nuts balls cock dick pussy weed pot coke crack joint shot stoned hung wood erect bang bone blow suck sucks screw nail sack bean beaver bush lemon melon peach plum tool
nick nik tom tim ben dan max sam ken jim bob joe jan dec nov
mine craft creeper enderman zombie skeleton spider villager pillager
""".split())


def _in(cp, script):
    return any(a <= cp <= b for a, b in RANGES[script])


def script_of(ch):
    cp = ord(ch)
    for s in RANGES:
        if _in(cp, s):
            return s
    return None


def fold(w):
    """What the matcher effectively compares: accents removed, lower-case."""
    d = unicodedata.normalize("NFD", w)
    d = "".join(c for c in d if not unicodedata.combining(c))
    return d.lower().replace("ß", "ss")


# --------------------------------------------------------------- tiny reader
def _unquote(s):
    s = s.strip()
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        out, i, body = [], 0, s[1:-1]
        while i < len(body):
            c = body[i]
            if c == "\\" and i + 1 < len(body):
                n = body[i + 1]
                out.append({"n": "\n", "t": "\t"}.get(n, n))
                i += 2
            else:
                out.append(c)
                i += 1
        return "".join(out)
    if len(s) >= 2 and s[0] == "'" and s[-1] == "'":
        return s[1:-1].replace("''", "'")
    return s


def _split_flow(body, errors, where):
    """Split the inside of [ ... ] on commas that are outside quotes."""
    items, cur, quote, esc = [], [], None, False
    for ch in body:
        if quote:
            cur.append(ch)
            if quote == '"' and esc:
                esc = False
            elif quote == '"' and ch == "\\":
                esc = True
            elif ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
            cur.append(ch)
        elif ch == ",":
            items.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if quote:
        errors.append("%s: unterminated quote in list" % where)
    items.append("".join(cur))
    return [_unquote(i) for i in items if i.strip()]


def read_simple(path, errors):
    text = open(path, encoding="utf-8").read()
    data, lines, i = {}, text.split("\n"), 0
    while i < len(lines):
        line = lines[i]
        if not line.strip() or line.lstrip().startswith("#"):
            i += 1
            continue
        m = re.match(r"^([A-Za-z0-9_-]+):\s*(.*)$", line)
        if not m or line.startswith(" "):
            errors.append("%s:%d: cannot read this line: %r" % (path, i + 1, line[:60]))
            i += 1
            continue
        key, rest = m.group(1), m.group(2).strip()
        if key in data:
            errors.append("%s:%d: key %r appears twice" % (path, i + 1, key))
        if rest.startswith("["):
            buf = rest
            while "]" not in _strip_quoted(buf) and i + 1 < len(lines):
                i += 1
                buf += "\n" + lines[i]
            if not buf.rstrip().endswith("]"):
                errors.append("%s:%d: list must end with ]" % (path, i + 1))
            inner = buf.strip()[1:-1] if buf.rstrip().endswith("]") else buf.strip()[1:]
            data[key] = _split_flow(inner.replace("\n", " "), errors, path)
        elif rest == "":
            vals = []
            while i + 1 < len(lines) and re.match(r"^\s+-\s", lines[i + 1]):
                i += 1
                vals.append(_unquote(re.sub(r"^\s+-\s+", "", lines[i])))
            data[key] = vals
        else:
            if "  #" in rest and not rest.startswith(("\"", "'")):
                rest = rest.split("  #")[0]
            data[key] = _unquote(rest)
        i += 1
    return data


def _strip_quoted(s):
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', s)


# ------------------------------------------------------------------- checks
def notes_tokens(notes, marker):
    """Words listed after 'marker:' in the notes (every occurrence is merged)."""
    out = set()
    for m in re.finditer(marker + r":\s*((?:(?!\b[a-z]+-ok:).)*)", notes or "", re.S):
        out.update(t for t in re.split(r"[\s,;]+", m.group(1).strip()) if t)
    return out


def check_file(path, data, errors, warnings):
    name = os.path.basename(path)
    code = name[:-4]
    for k in REQUIRED:
        if k not in data:
            errors.append("%s: missing key %r" % (name, k))
    if any(k not in data for k in REQUIRED):
        return None
    if data["language"] != code:
        errors.append("%s: language %r must equal the file name %r" % (name, data["language"], code))
    if data["script"] not in SCRIPTS:
        errors.append("%s: unknown script %r" % (name, data["script"]))
    if data["review-status"] not in STATUSES:
        errors.append("%s: review-status %r not in %s" % (name, data["review-status"], sorted(STATUSES)))
    if not isinstance(data["sources"], list) or not data["sources"]:
        errors.append("%s: sources must be a non-empty list" % name)
    for t in TIERS:
        if not isinstance(data[t], list):
            errors.append("%s: %s must be a list" % (name, t))
            return None
    is_custom = code == "custom"
    if not is_custom:
        for t in ("level-2", "level-3"):
            if not data[t]:
                errors.append("%s: %s is empty" % (name, t))
        if not data["level-1"]:
            warnings.append("%s: level-1 is empty" % name)
    notes = data["notes"] if isinstance(data["notes"], str) else ""
    short_ok = notes_tokens(notes, "short-ok")
    script = data["script"] if data["script"] in SCRIPTS else "latin"
    allowed = ALLOWED_WITH[script] | {"latin"}
    seen = {}
    native = total = 0
    for t in TIERS:
        for w in data[t]:
            where = "%s %s %r" % (name, t, w)
            if not isinstance(w, str) or not w:
                errors.append("%s: empty or non-text entry" % where)
                continue
            total += 1
            if w != unicodedata.normalize("NFC", w):
                errors.append("%s: not NFC-normalised" % where)
            if w != w.lower():
                errors.append("%s: not lower-case" % where)
            if w != " ".join(w.split()):
                errors.append("%s: has leading, trailing or double spaces" % where)
            if w in seen:
                errors.append("%s: duplicate (also in %s)" % (where, seen[w]))
            seen[w] = t
            letters = [c for c in w if c not in PUNCT_OK]
            has_wide = any(script_of(c) in TWO_CHAR_SCRIPTS and script_of(c) is not None
                           for c in letters)
            minlen = 2 if has_wide else 3
            if len("".join(letters)) < minlen and w not in short_ok and not is_custom:
                errors.append("%s: shorter than %d characters (add to 'short-ok:' in notes only for legacy words)" % (where, minlen))
            scripts_in_word = set()
            for c in letters:
                if c.isdigit():
                    continue
                s = script_of(c)
                if s is None:
                    if unicodedata.category(c).startswith("M"):
                        continue
                    errors.append("%s: character %r (U+%04X) is not in any supported script" % (where, c, ord(c)))
                    continue
                if s not in allowed:
                    errors.append("%s: character %r belongs to script %s, but the file is %s" % (where, c, s, script))
                scripts_in_word.add(s)
            if script != "latin" and "latin" in scripts_in_word and len(scripts_in_word) > 1:
                errors.append("%s: mixes Latin and %s letters in one word" % (where, script))
            if script == "kana" and scripts_in_word and scripts_in_word <= {"kana", "han"}:
                native += 1
            elif script != "latin" and (scripts_in_word - {"latin"}):
                native += 1
    if script != "latin" and total and native == 0:
        errors.append("%s: script is %s but no entry uses it" % (name, script))
    elif script != "latin" and total and native * 4 < total:
        warnings.append("%s: only %d of %d entries use the %s script" % (name, native, total, script))
    # English common words
    if code not in ("en", "en-gb") and not is_custom:
        for t in TIERS:
            for w in data[t]:
                if w in ENGLISH_COMMON and w not in notes_tokens(notes, "english-ok") and not re.search(r"(?<![\w])" + re.escape(w) + r"(?![\w])", notes):
                    errors.append("%s %s %r: is a common English word; remove it or mention it in notes" % (name, t, w))
    return data


def main(argv):
    quiet = "--quiet" in argv
    strict = "--strict" in argv
    args = [a for a in argv[1:] if not a.startswith("--")]
    folder = args[0] if args else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    folder = os.path.abspath(folder)
    errors, warnings, files = [], [], {}
    names = sorted(n for n in os.listdir(folder) if n.endswith(".yml"))
    if not names:
        print("no .yml files found in %s" % folder)
        return 1
    try:
        import yaml  # optional cross-check
    except Exception:
        yaml = None
    for n in names:
        p = os.path.join(folder, n)
        e0 = len(errors)
        data = read_simple(p, errors)
        if yaml is not None:
            try:
                y = yaml.safe_load(open(p, encoding="utf-8"))
                for k in REQUIRED:
                    if k in data and k in y and data[k] != y[k]:
                        errors.append("%s: built-in reader and PyYAML disagree on %r" % (n, k))
            except Exception as ex:
                errors.append("%s: PyYAML cannot parse the file: %s" % (n, ex))
        data = check_file(p, data, errors, warnings)
        if data is not None:
            files[n[:-4]] = data
    # cross-language collisions
    index = {}
    for code, d in files.items():
        if code == "custom":
            continue
        for t in TIERS:
            for w in d[t]:
                index.setdefault(fold(w), {}).setdefault(code, (t, w))
    unjustified, justified, pairs = [], 0, {}
    for key, langs in sorted(index.items()):
        if len(langs) < 2:
            continue
        codes = sorted(langs)
        ok = True
        for i in range(len(codes)):
            for j in range(i + 1, len(codes)):
                a, b = codes[i], codes[j]
                na = files[a]["notes"] or ""
                nb = files[b]["notes"] or ""
                good = (b in notes_tokens(na, "collisions-ok") or a in notes_tokens(nb, "collisions-ok")
                        or key in notes_tokens(na, "collisions-ok") or key in notes_tokens(nb, "collisions-ok")
                        or re.search(r"(?<![\w])" + re.escape(key) + r"(?![\w])", fold(na))
                        or re.search(r"(?<![\w])" + re.escape(key) + r"(?![\w])", fold(nb)))
                pairs.setdefault((a, b), [0, 0])
                pairs[(a, b)][0] += 1
                if not good:
                    pairs[(a, b)][1] += 1
                    ok = False
        if ok:
            justified += 1
        else:
            unjustified.append((key, {c: langs[c][1] for c in codes}))
    if not quiet:
        print("Cross-language collisions (same word after removing accents): %d words in 2+ languages"
              % sum(1 for l in index.values() if len(l) > 1))
        print("  justified in notes (collisions-ok: ...): %d" % justified)
        print("  NOT justified: %d" % len(unjustified))
        for (a, b), (n, u) in sorted(pairs.items(), key=lambda kv: -kv[1][0])[:25]:
            print("  pair %-6s %-6s shared=%-3d unjustified=%d" % (a, b, n, u))
        for key, langs in unjustified:
            print("  COLLISION %r in %s" % (key, ", ".join("%s(%s)" % (c, w) for c, w in langs.items())))
    for w in warnings:
        print("warning: " + w)
    for e in errors:
        print("ERROR: " + e)
    lang_files = [c for c in files if c != "custom"]
    print("checked %d files (%d languages + custom), %d errors, %d warnings"
          % (len(files), len(lang_files), len(errors), len(warnings)))
    if errors or (strict and unjustified):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

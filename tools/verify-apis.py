#!/usr/bin/env python3
"""Downloads the third-party plugin jars named in tools/api-manifest.txt and checks every referenced class/member with javap.
Usage: verify-apis.py [manifest] [--jars DIR] [--report FILE] [--offline]   (python3 stdlib only; needs javap)
Exit code 1 if any non-advisory entry FAILs (including entries whose jar could not be downloaded)."""
import os, re, subprocess, sys, urllib.request, xml.etree.ElementTree as ET


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "abw-api-verify"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def resolve(src):
    """Returns a jar URL for one source string."""
    p = src.split()
    if p[0] == "url":
        return p[1]
    if p[0] != "mvn":
        raise ValueError("bad source " + src)
    repo, group, art = p[1].rstrip("/"), p[2], p[3]
    base = "%s/%s/%s" % (repo, group.replace(".", "/"), art)
    ver = p[4] if len(p) > 4 else None
    if ver is None:
        root = ET.fromstring(fetch(base + "/maven-metadata.xml"))
        ver = (root.findtext("versioning/release") or root.findtext("versioning/latest")
               or [v.text for v in root.iter("version")][-1])
    if ver.endswith("-SNAPSHOT"):
        root = ET.fromstring(fetch("%s/%s/maven-metadata.xml" % (base, ver)))
        for sv in root.iter("snapshotVersion"):
            if sv.findtext("extension") == "jar" and not sv.findtext("classifier"):
                return "%s/%s/%s-%s.jar" % (base, ver, art, sv.findtext("value"))
        raise ValueError("no jar in snapshot metadata")
    return "%s/%s/%s-%s.jar" % (base, ver, art, ver)


def download(name, sources, jars_dir):
    dest = os.path.join(jars_dir, name + ".jar")
    errs = []
    for src in sources:
        try:
            url = resolve(src)
            data = fetch(url)
            if data[:2] != b"PK":
                raise ValueError("not a jar")
            with open(dest, "wb") as f:
                f.write(data)
            return dest, url, None
        except Exception as ex:  # one source failing must not stop the others
            errs.append("%s -> %s: %s" % (src, type(ex).__name__, ex))
    return None, None, "; ".join(errs)


def javap(jar, cls, cache):
    key = (jar, cls)
    if key not in cache:
        r = subprocess.run(["javap", "-public", "-cp", jar, cls], capture_output=True, text=True)
        cache[key] = r.stdout if r.returncode == 0 else None
    return cache[key]


def simple(t):
    return t.replace("...", "[]").split(".")[-1].strip()


def split_params(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch == "<":
            depth += 1
        if ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return [simple(re.sub(r"<.*>", "", x)) for x in out]


def check_member(out, cls, member):
    if member == "-":
        return True, "class present"
    if member == "annotation":
        return "@interface" in out or "interface " in out, "annotation"
    if member.startswith("field "):
        n = member[6:]
        return any(re.search(r"\b%s;" % re.escape(n), l) for l in out.splitlines()), member
    m = re.match(r"(\w+)\((.*)\)$", member)
    if not m:
        return False, "bad manifest member " + member
    name, want = m.group(1), split_params(m.group(2))
    for l in out.splitlines():
        mm = re.search(r"(?<![\w.])%s\((.*?)\)" % re.escape(name), l)
        if not mm:
            continue
        got = split_params(mm.group(1))
        if len(got) == len(want) and all(w == "*" or w == g for w, g in zip(want, got)):
            return True, l.strip()
    cands = [l.strip() for l in out.splitlines() if re.search(r"\b%s\(" % re.escape(name), l)]
    return False, "not found; same-name members: " + (" | ".join(cands) or "none")


def main():
    args = sys.argv[1:]
    here = os.path.dirname(os.path.abspath(__file__))
    manifest = os.path.join(here, "api-manifest.txt")
    jars_dir, report = "jars", "api-report.md"
    offline = "--offline" in args
    i = 0
    while i < len(args):
        if args[i] == "--jars":
            jars_dir = args[i + 1]
            i += 1
        elif args[i] == "--report":
            report = args[i + 1]
            i += 1
        elif not args[i].startswith("--"):
            manifest = args[i]
        i += 1
    os.makedirs(jars_dir, exist_ok=True)
    jars, entries = {}, []
    with open(manifest, encoding="utf-8") as mf:
        for line in mf:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("@jar"):
                name, srcs = line[4:].split("|", 1)
                jars[name.strip()] = [s.strip() for s in srcs.split(";")]
            else:
                jar, cls, mem = [x.strip() for x in line.split("|", 2)]
                entries.append((jar.lstrip("?"), jar.startswith("?"), cls, mem))

    paths, dl_lines, dl_failed = {}, [], set()
    for name, srcs in jars.items():
        if offline:
            p = os.path.join(jars_dir, name + ".jar")
            res = (p, "local", None) if os.path.exists(p) else (None, None, "offline and no local jar")
        else:
            res = download(name, srcs, jars_dir)
        if res[0]:
            paths[name] = res[0]
            dl_lines.append("| %s | OK | %s |" % (name, res[1]))
            print("DOWNLOAD OK   %s  %s" % (name, res[1]))
        else:
            dl_failed.add(name)
            dl_lines.append("| %s | **FAILED** | %s |" % (name, res[2]))
            print("DOWNLOAD FAIL %s  %s" % (name, res[2]))

    cache, rows, fails, warns = {}, [], 0, 0
    for jar, advisory, cls, mem in entries:
        if jar not in paths:
            status, detail = ("WARN" if advisory else "FAIL"), "jar not downloaded"
        else:
            out = javap(paths[jar], cls, cache)
            if out is None:
                ok, detail = False, "class not found in jar"
            else:
                ok, detail = check_member(out, cls, mem)
            status = "PASS" if ok else ("WARN" if advisory else "FAIL")
        fails += status == "FAIL"
        warns += status == "WARN"
        rows.append("| %s | %s | `%s` | `%s` | %s |" % (status, jar, cls, mem, detail.replace("|", "\\|")))
        print("%s  %s  %s  %s  (%s)" % (status, jar, cls, mem, detail))
    with open(report, "w", encoding="utf-8") as f:
        f.write("# API verification report\n\n%d entries, %d FAIL, %d WARN (advisory or jar missing), %d jars not downloaded\n\n"
                % (len(entries), fails, warns, len(dl_failed)))
        f.write("## Downloads\n\n| jar | result | url / error |\n|---|---|---|\n" + "\n".join(dl_lines) + "\n\n")
        f.write("## Entries\n\n| status | jar | class | member | detail |\n|---|---|---|---|---|\n" + "\n".join(rows) + "\n")
    print("\n%d entries: %d FAIL, %d WARN. Report: %s" % (len(entries), fails, warns, report))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())

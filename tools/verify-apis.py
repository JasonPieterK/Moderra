#!/usr/bin/env python3
"""Downloads the third-party plugin jars named in tools/api-manifest.txt and checks every referenced class/member with javap.
Usage: verify-apis.py [manifest] [--jars DIR] [--report FILE] [--offline] [--dump CLASSLIST]
--dump: instead of checking, print `javap -public` of every "<jar> | <class>" line of CLASSLIST (inherited members are NOT merged; the
header shows extends/implements) into stdout, each headed by =====CLASS <jar> <class>.   (python3 stdlib only; needs javap)
Exit code 1 if any non-advisory entry FAILs (including entries whose jar could not be downloaded)."""
import json, os, re, subprocess, sys, urllib.request, zipfile, xml.etree.ElementTree as ET


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "moderra-api-verify"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def resolve(src):
    """Returns a jar URL for one source string."""
    p = src.split()
    if p[0] == "url":
        return p[1]
    if p[0] == "ghrelease":  # ghrelease <owner/repo> <regex on asset name>
        hdr = {"Accept": "application/vnd.github+json"}
        if os.environ.get("GITHUB_TOKEN"):
            hdr["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
        req = urllib.request.Request("https://api.github.com/repos/%s/releases?per_page=15" % p[1],
                                     headers=dict(hdr, **{"User-Agent": "moderra-api-verify"}))
        rels = json.loads(urllib.request.urlopen(req, timeout=60).read())
        rx = re.compile(p[2])
        for rel in rels:
            if rel.get("prerelease") and len(rels) > 1:
                continue
            for a in rel.get("assets", []):
                n = a["name"]
                if rx.search(n) and n.endswith(".jar") and not re.search(r"sources|javadoc", n, re.I):
                    return a["browser_download_url"]
        raise ValueError("no matching release asset")
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


def javap(cp, cls, cache):
    """javap -public of cls with the whole classpath; also tries Outer$Inner for dotted nested names."""
    key = (cp, cls)
    if key not in cache:
        cache[key] = None
        parts = cls.split(".")
        for k in range(len(parts), 0, -1):
            name = ".".join(parts[:k]) + "".join("$" + x for x in parts[k:])
            r = subprocess.run(["javap", "-public", "-cp", cp, name], capture_output=True, text=True)
            if r.returncode == 0:
                cache[key] = r.stdout
                break
    return cache[key]


def strip_generics(t):
    while True:
        n = re.sub(r"<[^<>]*>", "", t)
        if n == t:
            return t
        t = n


def supertypes(out):
    """Class names after extends/implements in the javap header."""
    for l in out.splitlines():
        if re.search(r"\b(class|interface|enum)\s", l) and l.rstrip().endswith("{"):
            h = strip_generics(l.rstrip()[:-1])
            res = []
            for kw in ("extends", "implements"):
                m = re.search(r"\b%s\s+(.*?)(?=\s+(?:extends|implements)\s|$)" % kw, h)
                if m:
                    res += [x.strip() for x in m.group(1).split(",") if x.strip()]
            return res
    return []


def check_hierarchy(cp, cls, member, cache):
    """Checks member on cls, then on every super type reachable on the classpath. Returns (ok, detail)."""
    seen, todo, firstdetail = set(), [cls], None
    while todo:
        c = todo.pop(0)
        if c in seen or c.startswith("java."):
            continue
        seen.add(c)
        out = javap(cp, c, cache)
        if out is None:
            continue
        ok, detail = check_member(out, c, member)
        if ok:
            return True, detail + ("" if c == cls else "  [inherited from %s]" % c)
        if firstdetail is None:
            firstdetail = detail
        todo += supertypes(out)
    if firstdetail is None:
        return None, "class not found"
    return False, firstdetail + "  [searched: %s]" % ", ".join(sorted(seen))


def packages(jar):
    """Top-level package list (first 4 segments) of a jar."""
    try:
        names = zipfile.ZipFile(jar).namelist()
    except Exception as ex:
        return "cannot read jar: %s" % ex
    pk = sorted({"/".join(n.split("/")[:-1][:4]).replace("/", ".") for n in names if n.endswith(".class") and "/" in n})
    return ", ".join(pk[:60]) + (" ... (%d total)" % len(pk) if len(pk) > 60 else "")


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


def dump_classes(listfile, cp, paths):
    cache = {}
    with open(listfile, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            jar, cls = [x.strip() for x in line.split("|", 1)]
            print("=====CLASS %s %s" % (jar, cls))
            out = javap(cp, cls, cache) if paths else None
            if out is None:
                print("NOT FOUND (jar %s)" % ("present; packages: " + packages(paths[jar]) if jar in paths else "not downloaded"))
            else:
                print(out)
    return 0


def main():
    args = sys.argv[1:]
    here = os.path.dirname(os.path.abspath(__file__))
    manifest = os.path.join(here, "api-manifest.txt")
    jars_dir, report = "jars", "api-report.md"
    offline = "--offline" in args
    dump = None
    i = 0
    while i < len(args):
        if args[i] == "--jars":
            jars_dir = args[i + 1]
            i += 1
        elif args[i] == "--report":
            report = args[i + 1]
            i += 1
        elif args[i] == "--dump":
            dump = args[i + 1]
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

    cp = os.pathsep.join(os.path.abspath(x) for x in paths.values())
    if dump:
        return dump_classes(dump, cp, paths)
    cache, rows, fails, warns = {}, [], 0, 0
    for jar, advisory, cls, mem in entries:
        if jar not in paths:
            status, detail = ("WARN" if advisory else "FAIL"), "jar not downloaded"
        else:
            ok, detail = check_hierarchy(cp, cls, mem, cache)
            if ok is None:
                ok, detail = False, "class not found in jar; packages in %s.jar: %s" % (jar, packages(paths[jar]))
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

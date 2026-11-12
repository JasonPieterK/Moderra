#!/usr/bin/env python3
"""Print a table of the AntiBadWords word lists: language, script, review status, words per tier, total.

Usage:  python3 7-wordlists/tools/stats.py [folder]

Python 3 standard library only. Uses the same small reader as validate.py.
"""
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from validate import read_simple  # noqa: E402


def main(argv):
    folder = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    folder = os.path.abspath(folder)
    rows, custom, errors = [], None, []
    for n in sorted(os.listdir(folder)):
        if not n.endswith(".yml"):
            continue
        d = read_simple(os.path.join(folder, n), errors)
        counts = [len(d.get("level-%d" % i, [])) for i in (1, 2, 3)]
        row = (n[:-4], d.get("script", "?"), d.get("review-status", "?"), counts)
        if n == "custom.yml":
            custom = row
        else:
            rows.append(row)
    head = "%-6s %-11s %-12s %7s %7s %7s %7s" % ("lang", "script", "review", "tier-1", "tier-2", "tier-3", "total")
    print(head)
    print("-" * len(head))
    tot = [0, 0, 0]
    for code, script, status, c in rows:
        print("%-6s %-11s %-12s %7d %7d %7d %7d" % (code, script, status, c[0], c[1], c[2], sum(c)))
        for i in range(3):
            tot[i] += c[i]
    print("-" * len(head))
    print("%-6s %-11s %-12s %7d %7d %7d %7d" % ("TOTAL", "", "", tot[0], tot[1], tot[2], sum(tot)))
    print()
    print("%d language files" % len(rows))
    if custom:
        print("custom.yml (the admin's own words, empty by design): %d words" % sum(custom[3]))
    for e in errors:
        print("ERROR: " + e)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

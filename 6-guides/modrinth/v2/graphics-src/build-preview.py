#!/usr/bin/env python3
"""Builds ../DESCRIPTION-preview.html from ../DESCRIPTION.md with the local PNGs. Needs: pip install markdown"""
import glob, os, re
import markdown
here = os.path.dirname(os.path.abspath(__file__))
root = os.path.dirname(here)
md = open(os.path.join(root, "DESCRIPTION.md"), encoding="utf-8").read()
for n in range(1, 10):
    f = glob.glob(os.path.join(root, "images", f"{n:02d}-*.png"))[0]
    md = md.replace(f"IMAGE_URL_{n:02d}", "images/" + os.path.basename(f))
md = re.sub(r"https://raw\.githubusercontent\.com/[^)\s]*/6-guides/modrinth/v2/images/", "images/", md)  # preview uses the local PNGs
md = md.replace("<details>", "<details markdown=\"1\">")
html = markdown.markdown(md, extensions=["tables", "fenced_code", "md_in_html", "toc"])
css = """body{margin:0;background:#1b1b1b;font:16px/1.55 system-ui,sans-serif;color:#e6e6e6}
.page{max-width:900px;margin:0 auto;padding:24px 16px 80px}
h2{border-bottom:3px solid #F2C230;padding-bottom:4px;margin-top:2em}
img{max-width:100%;height:auto;display:block;margin:16px 0}
table{border-collapse:collapse;width:100%;margin:12px 0;display:block;overflow-x:auto}
td,th{border:1px solid #555;padding:6px 10px;text-align:left}th{background:#2c2c2c}
code{background:#2c2c2c;padding:1px 5px;border-radius:4px}pre{background:#2c2c2c;padding:12px;overflow-x:auto}pre code{padding:0}
blockquote{border-left:6px solid #D6352B;margin:12px 0;padding:2px 14px;background:#262626}
details{border:1px solid #555;padding:8px 12px;margin:8px 0}summary{cursor:pointer;font-weight:700}a{color:#7fb0ff}"""
open(os.path.join(root, "DESCRIPTION-preview.html"), "w", encoding="utf-8").write(
    f'<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
    f'<title>Moderra - description preview</title><style>{css}</style></head><body><div class="page">{html}</div></body></html>')
print("ok")

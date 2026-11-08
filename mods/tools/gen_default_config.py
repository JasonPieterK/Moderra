#!/usr/bin/env python3
"""Generates mods/core/src/main/resources/antibadwords-default.json from the Bukkit config.yml,
so the Fabric and Forge mods ship exactly the same word lists and defaults as the plugin.
Usage: python3 mods/tools/gen_default_config.py"""
import json, pathlib, yaml

root = pathlib.Path(__file__).resolve().parents[2]
cfg = yaml.safe_load((root / "src/main/resources/config.yml").read_text(encoding="utf-8"))

commands = []
for key, e in (cfg["punishment"].get("effects") or {}).items():
    commands.append({"id": key, "enabled": bool(e["enabled"]),
        "command": f"effect give {{player}} {e['effect-name']} {e['duration']} {e['amplifier']} true"})
for key, s in (cfg["punishment"].get("sounds") or {}).items():
    commands.append({"id": key, "enabled": bool(s["enabled"]),
        "command": f"playsound minecraft:{s['sound-name'].replace('minecraft:', '')} master {{player}} ~ ~ ~ {s['volume']} {s['pitch']}"})
for key, c in (cfg["punishment"].get("commands") or {}).items():
    commands.append({"id": key, "enabled": bool(c["enabled"]), "command": c["command"]})

m = cfg["filters"]["matching"]
out = {
    "protection-level": cfg["filters"]["protection-level"],
    "enabled-word-lists": cfg["filters"]["enabled-word-lists"],
    "matching": {
        "collapse-repeated-letters": m["collapse-repeated-letters"],
        "repeated-letter-threshold": m["repeated-letter-threshold"],
        "detect-spaced-letters": m["detect-spaced-letters"],
        "join-adjacent-words": m["join-adjacent-words"],
        "partial-match": m["partial-match"],
        "partial-min-length": m["partial-min-length"],
    },
    "whitelist": cfg["filters"]["whitelist"],
    "scan": {"chat": True, "commands": True},
    "messages": {
        "broadcast-warnings": cfg["messages"]["broadcast-warnings"],
        "warning-broadcast-format": cfg["messages"]["warning-broadcast-format"],
        "warning-player": cfg["messages"]["warning-player"],
        "punishment-broadcast-format": cfg["messages"]["punishment-broadcast-format"],
        "punishment-player": cfg["messages"]["punishment-player"],
        "punishment-disabled-player": cfg["messages"]["punishment-disabled-player"],
        "notify-staff": cfg["messages"]["notify-staff"],
        "staff-notify-format": cfg["messages"]["staff-notify-format"],
    },
    "punishment": {
        "enabled": cfg["punishment"]["enabled"],
        "violations-before-punish": cfg["punishment"]["violations-before-punish"],
        "clear-violations-on-quit": cfg["punishment"]["clear-violations-on-quit"],
        "final-command": cfg["punishment"]["final-command"]["command"],
        "final-command-enabled": cfg["punishment"]["final-command"]["enabled"],
        "commands": commands,
    },
    "custom-words": list(cfg["word-lists"].get("custom") or []),
    "word-lists": {k: v for k, v in cfg["word-lists"].items() if k != "custom"},
}
dest = root / "mods/core/src/main/resources/antibadwords-default.json"
dest.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("wrote", dest, "-", len(commands), "punishment commands,", len(out["word-lists"]), "word lists")

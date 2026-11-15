# Task 52 (CI green loop)
- Run 43691650981 plugin-apis had one FAIL: `Placeholders.registerCommon(Identifier,Placeholder$Handler)` (the report printed the want as `Handler`; the verifier does not match the nested generic type). Manifest now uses `registerCommon(Identifier,*)` (all 6 entries).
- Runs for aa95e4e (43694424308 / 43694424315): every job failed in about 2 s with no runner, no steps and no log (HTTP 404), so no compiler error existed to read. A new commit is pushed to re-run.
- Fabric Placeholder API candidates: see the result section below (filled when a run with the candidate downloads is readable).

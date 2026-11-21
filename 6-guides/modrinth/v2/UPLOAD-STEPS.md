# Upload steps: Moderra Modrinth page

Do these steps once. You need the project owner account on Modrinth. Time: about 5 minutes.

## Quick way (recommended)

The repository is public. `DESCRIPTION.md` already loads its nine pictures from the `v1.0` release tag on GitHub
(`raw.githubusercontent.com/.../v1.0/6-guides/modrinth/v2/images/...`). These links do not change.

1. Open the Modrinth project, then **Settings -> General**. Upload `6-guides/branding/logo-a-refined/moderra-icon-512.png` as the
   project icon. Set the project name to **Moderra**.
2. Open **Settings -> Description**. Delete the old text. Paste the full content of `DESCRIPTION.md`. Click **Preview** and check
   that all pictures show. Save.
3. Optional: upload the pictures to the **Gallery** too, so they show on the Gallery tab.
4. Open **Versions** and upload the jars from the GitHub release `v1.0`: each `Moderra<mc>-1.0-<loader>.jar` with its loader
   (Bukkit, Spigot, Paper, Purpur, Fabric, Forge) and its Minecraft version.

If you rename the GitHub repository later, GitHub redirects the old picture links, so the page keeps working.

## Other way: pictures from the Modrinth Gallery

Use this only if you do not want the page to load pictures from GitHub. Then replace each GitHub picture link in
`DESCRIPTION.md` with the Gallery link of the same file.

Picture to file:

| Picture | File | Used for |
|---|---|---|
| 01 | `images/01-hero.png` | Banner at the top |
| 02 | `images/02-where.png` | Where it checks |
| 03 | `images/03-pipeline.png` | Filter pipeline |
| 04 | `images/04-ladder.png` | Escalation ladder |
| 05 | `images/05-matrix.png` | Platforms and versions |
| 06 | `images/06-languages.png` | Languages |
| 07 | `images/07-menu.png` | In-game menu |
| 08 | `images/08-integrations.png` | Integrations |
| 09 | `images/09-divider.png` | Thin divider (used three times) |

## Step 1: Upload the pictures to the Gallery

1. Sign in to https://modrinth.com and open your Moderra project.
2. Open the project **Settings**. In the left menu, click **Gallery**.
3. Click **Add an image** (the button can also say **Upload**).
4. Choose `01-hero.png`. Type a title, for example `Banner`. Turn on "Featured" only if you want this picture as the main gallery picture.
5. Click **Add image** to save.
6. Do the same for `02-where.png` to `09-divider.png`. Use the subject of the picture as the title.
7. If Modrinth refuses a file, check the file size. Ours are smaller than 1 MB.

The Modrinth menu names can change. If a button has another name, look for the nearest match.

## Step 2: Copy the URL of each image

1. In the Gallery list, find the image.
2. Right-click the picture. Choose **Copy image address** (Chrome, Edge) or **Copy Image Link** (Firefox).
   Other way: open the picture in a new tab and copy the address from the address bar.
3. The URL looks like `https://cdn.modrinth.com/data/<project-id>/images/<hash>.png`.
4. Paste it into a text file. Write the number next to it, for example `03 = https://cdn.modrinth.com/...`.
5. Do this for all nine pictures. Open each URL in a browser tab and compare it with the table above. This shows if you mixed two pictures.

## Step 3: Replace the GitHub picture links (Gallery way only)

1. Open `DESCRIPTION.md` in a text editor.
2. Each picture link ends with its file name, for example `.../images/03-pipeline.png`. Replace each GitHub link with the
   Gallery URL of the same picture. `09-divider.png` appears three times: replace all three.
3. Search for `raw.githubusercontent.com`. The search must find nothing.
4. Save the file under a new name (for example `DESCRIPTION-final.md`). Do not change anything else.

## Step 4: Put the text on Modrinth

1. On Modrinth, open **Settings**, then **Description**.
2. Select all existing text and delete it.
3. Paste the full content of `DESCRIPTION-final.md`.
4. Click **Preview** in the editor. Check each picture. Check the tables and the dropdowns (`<details>`).
5. Click **Save changes**.
6. Open the public project page in a private window. Check it in the light theme and in the dark theme. The pictures have a black frame, so they read well in both.

## Step 5: Upload the jars (separate from this page)

The 36 jars come from the GitHub release `v1.0`. Name each file as on GitHub: `Moderra<mc>-1.0-<loader>.jar`. Set the loader (Bukkit, Spigot, Paper, Purpur, Fabric or Forge) and the game version on each upload.

## Checks before you publish

- The licence field on Modrinth says **All Rights Reserved**, not MIT. Use the custom licence option and link to the `LICENSE` file in the GitHub repository.
- The page says that version 1.0 is not yet tested on a live server. Change this part only after you test the jars on a copy of your world.
- The pictures have no Minecraft textures. The hero picture shows the Moderra logo.
- **Project icon:** in the project **Settings**, **General**, upload `6-guides/branding/logo-a-refined/moderra-icon-512.png` as the project icon (the chosen logo A2, 512 x 512 px).

## If you change a picture later

Edit `graphics-src/gen.py`. Then run these commands (Node with Playwright and its Chromium must be installed):

```
python3 graphics-src/gen.py
NODE_PATH=<your node_modules> node graphics-src/render.js
pip install markdown
python3 graphics-src/build-preview.py
```

Upload the new PNG in the Gallery. Delete the old one. Copy the new URL. Replace the old URL in the page text.

## Credits

- Icons: Phosphor Icons, MIT licence, phosphoricons.com (`@phosphor-icons/core` 2.1.1, Bold weight, one Fill icon). The SVGs and the
  licence text are in `graphics-src/phosphor/`. Run `python3 gen.py` then `node render.js` in `graphics-src/` to rebuild the pictures.

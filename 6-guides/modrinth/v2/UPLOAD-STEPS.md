# Upload steps: AntiBadWords 2 Modrinth page

Do these steps once. You need the project owner account on Modrinth. Time: about 15 minutes.

## What you have

- `DESCRIPTION.md`: the page text. It has nine placeholders: `IMAGE_URL_01` to `IMAGE_URL_09`.
- `images/01-hero.png` to `images/09-divider.png`: the nine pictures (each smaller than 1 MB).
- `DESCRIPTION-preview.html`: open it in a browser to read the page with the local pictures. Check the text before you upload.

Placeholder to file:

| Placeholder | File | Used for |
|---|---|---|
| `IMAGE_URL_01` | `images/01-hero.png` | Banner at the top |
| `IMAGE_URL_02` | `images/02-where.png` | Where it checks |
| `IMAGE_URL_03` | `images/03-pipeline.png` | Filter pipeline |
| `IMAGE_URL_04` | `images/04-ladder.png` | Escalation ladder |
| `IMAGE_URL_05` | `images/05-matrix.png` | Platforms and versions |
| `IMAGE_URL_06` | `images/06-languages.png` | Languages |
| `IMAGE_URL_07` | `images/07-menu.png` | In-game menu |
| `IMAGE_URL_08` | `images/08-integrations.png` | Integrations |
| `IMAGE_URL_09` | `images/09-divider.png` | Thin divider (used three times) |

## Step 1: Upload the pictures to the Gallery

1. Sign in to https://modrinth.com and open your AntiBadWords project.
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

## Step 3: Replace the placeholders

1. Open `DESCRIPTION.md` in a text editor.
2. Use **Find and replace all**. Replace `IMAGE_URL_01` with the URL of picture 01. Do the same for 02 to 09.
   `IMAGE_URL_09` appears three times. Replace all three with the same URL.
3. Search for `IMAGE_URL`. The search must find nothing.
4. Save the file under a new name (for example `DESCRIPTION-final.md`). Do not change anything else.

## Step 4: Put the text on Modrinth

1. On Modrinth, open **Settings**, then **Description**.
2. Select all existing text and delete it.
3. Paste the full content of `DESCRIPTION-final.md`.
4. Click **Preview** in the editor. Check each picture. Check the tables and the dropdowns (`<details>`).
5. Click **Save changes**.
6. Open the public project page in a private window. Check it in the light theme and in the dark theme. The pictures have a black frame, so they read well in both.

## Step 5: Upload the jars (separate from this page)

The 24 jars come from the GitHub release `v2.0.0`. Name each file as on GitHub: `Antibadwords-RELEASE[<Loader>] - 2-<mc>.jar`. Set the loader (Bukkit, Spigot, Paper, Purpur, Fabric or Forge) and the game version on each upload.

## Checks before you publish

- The licence field on Modrinth says **All Rights Reserved**, not MIT. Use the custom licence option and link to the `LICENSE` file in the GitHub repository.
- The page says that version 2 is not yet tested on a live server. Change this part only after you test the jars on a copy of your world.
- The pictures have no logo and no Minecraft textures. Set a project icon yourself if you want one.

## If you change a picture later

Edit `graphics-src/gen.py`. Then run these commands (Node with Playwright and its Chromium must be installed):

```
python3 graphics-src/gen.py
NODE_PATH=<your node_modules> node graphics-src/render.js
pip install markdown
python3 graphics-src/build-preview.py
```

Upload the new PNG in the Gallery. Delete the old one. Copy the new URL. Replace the old URL in the page text.

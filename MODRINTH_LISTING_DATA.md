# Super Mod Menu — Modrinth Listing Data (real values)

Copy-paste ready. Every value below comes from the actual project
(`fabric.mod.json`, `gradle.properties`, built jar). No placeholders except the
one link marked ⚠ that you must confirm exists.

---

## Project fields

| Field | Value |
|-------|-------|
| Type | Mod |
| Name | Super Mod Menu |
| Slug / URL | `super-mod-menu` |
| Visibility | Public |
| Client side | Required |
| Server side | Unsupported |
| License | MIT |
| Categories | Utility, Management |
| Loaders | Fabric |
| Game version | 1.21.4 |
| Mod ID | supermodmenu |

---

## Summary (plain text — no formatting)

```
A focused, modern mod menu for Fabric with favorites, notes, an in-game mod browser, and a one-click update checker.
```

---

## Description (paste into the body editor)

> Images use raw GitHub URLs. After you push `img/img_1.png`, `img/img_2.png`,
> `img/img_3.png` to your repo, they render automatically here. If your default
> branch is `master`, change `main` → `master` in the URLs.

```markdown
![Super Mod Menu](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_1.png)

## Super Mod Menu

A clean, modern replacement for the in-game mod list. Manage your installed mods,
keep them up to date, and discover new ones — all from one polished screen.

## Features

- **Modern mod list** — searchable, with quick filters (All, Favorites, Updates)
  and Client / Server badges.
- **Rich detail panel** — icon, version, author, full description, handy
  Website / Source / Issues links, and a "Required by" line that explains why a
  library is installed.
- **Favorites** — star mods to pin them to the top of the list.
- **Notes** — attach a personal note to any mod.
- **Update checker** — instantly see which mods have a newer version, then update
  with a single click.
- **In-game mod browser** — search for new mods and install them without leaving
  the game.
- **Dependency view** — see what a mod needs and what needs it.
- **Open mods folder** in one click.
- **Mod Menu config integration** — open a mod's own settings screen when
  available.
- **Blurred menu background** for a clean, modern feel.

![Browse and install mods](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_2.png)

## How to use

- Open it from the title screen (bottom-right) or the pause menu: **Mods**.
- Left-click a mod to view details; right-click to add a note.
- **Check Updates** scans for newer versions; **Update** applies one.
- **Get Mods** lets you search and install new mods.
- **Open Folder** opens your mods folder.

![Update checker and details](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_3.png)

## Requirements

- Minecraft 1.21.4
- Fabric Loader 0.16.0 or newer
- Fabric API
- Optional: Mod Menu (enables the per-mod config button)

## Network use

Super Mod Menu connects to the internet to search for mods, download them, and
check installed mods for updates. It does not collect or transmit personal data.
Downloads are saved to your local mods folder and only happen when you choose to
install or update.

## Notes

- Installing an update or a new mod takes effect after restarting the game.
- This is a client-side mod and is not required on servers.
```

---

## Version (first upload)

| Field | Value |
|-------|-------|
| Version number | 1.0.0 |
| Version title | 1.0.0 — Initial Release |
| Release channel | Release |
| Loader | Fabric |
| Game version | 1.21.4 |
| File | `supermodmenu-1.0.0.jar` (in `build/libs/`) |

### Dependencies (set on the version)
| Dependency | Type |
|------------|------|
| Fabric API | Required |
| Mod Menu | Optional |

### Changelog
```markdown
First public release of Super Mod Menu.

- Clean mod list with search and filters (All / Favorites / Updates)
- Detail panel: icon, version, authors, description, "Required by"
- Favorites and personal notes
- Update checker with one-click update
- In-game mod browser to search and install mods
- Dependency view
- Open mods folder button
- Config integration with Mod Menu (optional)
- Blurred menu background on the main screen
```

---

## Icon
Upload: `img/logowithoutbg.png` (square, transparent). Modrinth resizes it.

---

## Links

| Type | URL | Status |
|------|-----|--------|
| Website | https://devxkamlesh.com | ✅ real |
| Source code | https://github.com/devxkamlesh/supermodmenu | ⚠ only add if this repo exists; otherwise leave blank |
| Issues | https://github.com/devxkamlesh/supermodmenu/issues | ⚠ same as above |

---

## Author
- Kamlesh Choudhary — https://devxkamlesh.com
- GitHub: https://github.com/devxkamlesh
- X: https://x.com/devxkamlesh
- LinkedIn: https://linkedin.com/in/devxkamlesh

---

## Build facts (for your reference — confirmed from the project)
- Built against: Minecraft 1.21.4, Yarn 1.21.4+build.8, Fabric Loader 0.16.9,
  Fabric API 0.119.2+1.21.4
- Output jar: `build/libs/supermodmenu-1.0.0.jar`
- 1.21.5: the jar runs on 1.21.5 after the icon fix, but it is compiled for
  1.21.4 and not fully verified on 1.21.5 — only add 1.21.5 to the game-version
  list once you've confirmed the screens render correctly there.
```

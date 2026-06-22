# Super Mod Menu — Modrinth Submission Pack

Everything needed to create and publish the project on Modrinth, in one place.
Copy each field into the matching box on the "Create a project" page and the
first "Create version" page.

---

## 1. What Modrinth requires (quick checklist)

### Project page — required to create
- [ ] **Project type** — Mod
- [ ] **Name** — the project's display name (name only, no taglines)
- [ ] **URL / slug** — unique, lowercase, hyphenated
- [ ] **Owner** — your account (or an organization)
- [ ] **Visibility** — Public / Unlisted / Private (after approval)
- [ ] **Summary** — 1–2 plain sentences, **no formatting**, must not repeat the title

### Required before it can be submitted for review
- [ ] **Description** (body) — English, plain-text readable; must explain
      (a) what it does, (b) why download it, (c) any critical info to know first
- [ ] **License** — choose a recognized license (MIT here)
- [ ] **Categories / tags** — accurate, consistent with the actual function
- [ ] **Environment** — client / server side support
- [ ] At least **one version** uploaded (file + loader + game version)

### Recommended (faster review, fewer issues)
- [ ] **Icon** (square PNG, 128×128+)
- [ ] **Gallery images**, each with a title
- [ ] **External links** — source, issues, homepage (all public + relevant)
- [ ] **Dependencies** declared on the version
- [ ] Metadata consistent everywhere (license, sides, tags)

### Rules that apply to this project (from Modrinth Content Rules)
- Clear & honest description; no misleading claims; title is name-only.
- Summary has no formatting and doesn't repeat the title.
- **Network disclosure:** any connection to a remote server must be clearly
  disclosed (this project connects to the internet — see the description's
  "Network use" line).
- Own the rights to everything uploaded (code, icon, images).
- Gallery images relevant + titled; external links public + relevant.
- Declare all dependencies in the version's Dependencies section.

---

## 2. Project fields (fill these in)

| Field | Value |
|-------|-------|
| **Type** | Mod |
| **Name** | Super Mod Menu |
| **URL / slug** | `super-mod-menu` *(fallbacks: `supermodmenu`, `super-modmenu`)* |
| **Owner** | devxkamlesh |
| **Visibility** | Public |
| **Client side** | Required |
| **Server side** | Unsupported |
| **License** | MIT |
| **Categories** | Management, Utility |
| **Loaders** | Fabric |
| **Game versions** | 1.21.4, 1.21.5 |

### Summary (plain text, no formatting — paste as-is)
```
A clean, modern mod menu for Fabric with favorites, notes, an in-game mod browser, and a one-click update checker.
```

---

## 3. Description (paste into the Description / body editor)

```markdown
Super Mod Menu is a lightweight, modern replacement for the in-game mod list. It
makes managing your installed mods fast and pleasant, and lets you discover and
install new ones without leaving the game.

## What it does
- **Clean mod list** — browse your installed mods with search and quick filters
  (All, Favorites, Updates).
- **Rich detail panel** — icon, version, authors, full description, and a
  "Required by" line so you can see why a library is installed.
- **Favorites** — star mods to pin them to the top.
- **Notes** — attach a personal note to any mod.
- **Update checker** — find out which of your mods have a newer version, with a
  one-click update that downloads the new build and applies it on the next
  restart.
- **In-game mod browser ("Get Mods")** — search for new mods and install them
  directly into your mods folder.
- **Dependency view** — see what a mod depends on and what depends on it.
- **Open mods folder** — jump straight to your mods folder from the menu.
- **Config integration** — if Mod Menu is installed, open a mod's own config
  screen directly.

## Why download it
It replaces the bare vanilla mod list with a focused, good-looking interface and
adds the things you actually want day to day: finding updates, installing mods,
and keeping your list organized — all in one screen.

## How to use
- Open it from the **title screen** (bottom-right) or the **pause menu**:
  `Mods`.
- Left-click a mod to see details; right-click to edit its note.
- Use **Check Updates** to scan for newer versions, then **Update** on a mod.
- Use **Get Mods** to search for and install new mods.
- Use **Open Folder** to open your mods folder.

## Requirements
- Minecraft 1.21.4 or 1.21.5
- Fabric Loader 0.16.0+
- Fabric API
- Optional: Mod Menu (enables the per-mod config button)

## Network use
Super Mod Menu connects to the internet to search for mods, download them, and
check your installed mods for updates. It does not collect or transmit personal
data. All downloads are saved to your local mods folder and only happen when you
choose to install or update.

## Notes
- Enabling/applying an installed update takes effect after restarting the game.
- This is a client-side mod; it is not required on servers.
```

---

## 4. First version (Create version page)

| Field | Value |
|-------|-------|
| **Version number** | 1.0.0 |
| **Version title** | 1.0.0 — Initial Release |
| **Release channel** | Release |
| **Loaders** | Fabric |
| **Game versions** | 1.21.4, 1.21.5 |
| **Primary file** | `supermodmenu-1.0.0.jar` (from `build/libs/`) |

### Dependencies (add in the version's Dependencies section)
| Dependency | Type |
|------------|------|
| Fabric API (`fabric-api`) | Required |
| Fabric Language Kotlin | Not required |
| Mod Menu | Optional |

### Changelog (paste into the version changelog)
```markdown
First public release of Super Mod Menu.

- Clean, modern mod list with search and filters (All / Favorites / Updates)
- Rich detail panel: icon, version, authors, description, "Required by"
- Favorites and personal notes
- Update checker with one-click update
- In-game mod browser ("Get Mods") to search and install mods
- Dependency view
- Open mods folder button
- Config integration with Mod Menu (optional)
- Menu blur background on the main screen
```

---

## 5. Links (External resources section)

| Link type | URL |
|-----------|-----|
| Source code | https://github.com/devxkamlesh/supermodmenu |
| Issue tracker | https://github.com/devxkamlesh/supermodmenu/issues |
| Homepage / Website | https://devxkamlesh.com |

> Make sure these are public before submitting. If the GitHub repo isn't created
> yet, either create it or leave the source/issues links blank rather than
> pointing to a 404.

---

## 6. Icon & gallery

- **Icon:** use `img/logowithoutbg.png` (square, transparent) — upload the full
  image; Modrinth resizes it.
- **Gallery (recommended, each needs a title):**
  1. "Main mod list" — the list with a mod selected and the detail panel.
  2. "Get Mods browser" — the in-game mod browser with results.
  3. "Update checker" — the list showing the updates filter / update badge.
  4. "Dependency view" — the dependency screen for a mod.

---

## 7. Tags / keywords (for the description or search relevance)
```
mod menu, mod manager, mod list, favorites, notes, update checker, mod browser, install mods, fabric, utility, management
```

---

## 8. Author / brand identity (for consistency across listings)

| Field | Value |
|-------|-------|
| Name | Kamlesh Choudhary |
| Handle | @devxkamlesh |
| Website | https://devxkamlesh.com |
| GitHub | https://github.com/devxkamlesh |
| LinkedIn | https://linkedin.com/in/devxkamlesh |
| X (Twitter) | https://x.com/devxkamlesh |

---

## 9. Pre-submit checklist
- [ ] Name, slug, summary filled (summary has no formatting, no title repeat)
- [ ] Description explains what / why / critical info, in English plain text
- [ ] License = MIT; client=Required, server=Unsupported
- [ ] Categories = Management, Utility
- [ ] Icon uploaded
- [ ] At least 1 gallery image, each titled
- [ ] Links are public and relevant (or left blank)
- [ ] Version 1.0.0 uploaded: Fabric, 1.21.4 + 1.21.5, primary jar, changelog
- [ ] Dependencies declared (Fabric API = Required, Mod Menu = Optional)
- [ ] Submit for review
```

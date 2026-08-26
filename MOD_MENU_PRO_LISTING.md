# Mod Menu Pro — Listing Content

## Project name

**Mod Menu Pro**

## Slug suggestion

`mod-menu-pro`

## Short summary

A polished in-game Fabric mod manager with search, favorites, notes, dependency insights, update checks, and Modrinth discovery.

## Full description

# Mod Menu Pro

**Manage, discover, and keep track of your mods without leaving Minecraft.**

![Mod Menu Pro](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_1.png)

Mod Menu Pro is a clean, modern mod management screen for Fabric. Browse installed mods, find important details, organize favorites, add personal notes, check for updates, and discover new projects from Modrinth—all from inside the game.

## Build Minecraft Mods with AI

[![Build your Minecraft mod with CubeWright AI](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/cubewright-ai-banner.png)](https://devxkamlesh.com/cubewright)

**CubeWright AI is coming soon—describe your mod idea and turn it into a buildable Minecraft project.**

## Features

- **Modern mod list** — quickly search installed mods and filter by All, Favorites, or Updates.
- **Rich mod details** — view the icon, version, authors, description, environment, and Website, Source, and Issues links.
- **Favorites** — pin frequently used mods to the top of the list.
- **Personal notes** — attach a private note to any installed mod.
- **Update checker** — detect newer compatible releases through Modrinth and open the correct update page.
- **In-game mod browser** — search Modrinth and install compatible Fabric mods directly into the mods folder.
- **Dependency insights** — inspect dependencies and see which installed mods require a selected project.
- **Config integration** — open a mod's configuration screen when Mod Menu provides one.
- **Quick folder access** — open the Minecraft mods folder with one click.
- **Client-side utility** — no server installation is required.

![Browse and install mods](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_2.png)

## Usage

- Open **Mods** from the title screen or pause menu.
- Left-click a mod to inspect its details.
- Right-click a mod to add or edit a note.
- Use **Updates** to scan installed files for newer compatible releases.
- Use **Get Mods** to search for and install Fabric mods from Modrinth.
- Restart Minecraft after installing a new mod.

![Update checker and mod details](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_3.png)

## Requirements

- **Minecraft:** 26.2
- **Fabric Loader:** 0.19.3 or newer
- **Fabric API:** Required
- **Java:** 25
- **Mod Menu:** Optional, for third-party configuration-screen integration

## Network access

Mod Menu Pro connects to Modrinth to search for projects, retrieve descriptions and icons, check installed files for updates, and download mods chosen by the user. No personal data is collected or transmitted. Downloads happen only after the user selects an install action.

## Notes

- New mod installations take effect after Minecraft is restarted.
- Updates are identified through Modrinth; the update action opens the relevant Modrinth project page for safe manual installation.
- Mod Menu Pro is a client-side mod and is not required on servers.
- Existing Super Mod Menu settings remain compatible because the internal mod ID is intentionally unchanged.

## Version information

| Field | Value |
|---|---|
| Version number | `1.2.0` |
| Version title | `Mod Menu Pro 1.2.0 — Minecraft 26.2 Upgrade` |
| Release channel | Release |
| Game version | Minecraft 26.2 |
| Loader | Fabric |
| Required dependency | Fabric API |
| Optional dependency | Mod Menu |
| Java version | Java 25 |
| File | `supermodmenu-1.2.0.jar` |

## Version description / changelog

# Mod Menu Pro 1.2.0

This release introduces the **Mod Menu Pro** name and fully upgrades the mod to Minecraft 26.2 while retaining every existing feature and existing user data.

### Changes

- Renamed the user-facing mod from **Super Mod Menu** to **Mod Menu Pro**.
- Fully migrated the interface to Minecraft 26.2's unobfuscated GUI, rendering, input, and texture APIs.
- Updated to Fabric Loader 0.19.3 and Fabric API 0.158.0+26.2.
- Updated the build system to Fabric Loom 1.17, Gradle 9.5.1, and Java 25.
- Preserved search, filters, favorites, personal notes, dependency views, update checking, Modrinth browsing, mod installation, icons, links, and config integration.
- Preserved the internal mod ID and config location so existing favorites and notes continue to work.
- Restricted compatibility metadata to Minecraft 26.2 instead of using an unsafe wildcard.

## Image files

- Main screenshot: `https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_1.png`
- Browser screenshot: `https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_2.png`
- Update/details screenshot: `https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/img_3.png`
- Project icon: `https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/logowithoutbg.png`

When publishing online, replace the relative image paths with the final raw URLs for the repository or upload the images directly through the platform editor.

## CubeWright AI banner generation prompt

Use the following prompt with an AI image generator. Upload the result to GitHub
as `img/cubewright-ai-banner.png`; the listing uses its raw GitHub URL.

> Create a premium ultra-wide promotional banner for “CubeWright AI”, an AI website
> that builds Minecraft mods from natural-language ideas. Show a tasteful voxel
> workshop scene with a friendly glowing AI crafting core, floating code panels,
> block and item blueprints, emerald-green energy, deep charcoal and navy
> background, subtle cyan highlights, cinematic lighting, crisp game-inspired
> pixel geometry, clean professional composition, and generous negative space.
> Include the headline “TURN YOUR IDEAS INTO MINECRAFT MODS” and smaller text
> “Describe it. CubeWright builds it.” Add a clear emerald
> call-to-action shape reading “COMING SOON”. Do not use official Minecraft logos,
> characters, UI screenshots, or copyrighted textures. 3:1 aspect ratio, 1800×600,
> sharp readable typography, no watermark.

## Reusable section for every A-to-Z mod listing

Copy this block near the end of each project description, before requirements or
credits. The website uses a path on the existing developer domain, so no separate
domain purchase is required.

```markdown
---

## Turn Your Ideas into Minecraft Mods

[![Build your Minecraft mod with CubeWright AI](https://raw.githubusercontent.com/devxkamlesh/supermodmenu/main/img/cubewright-ai-banner.png)](https://devxkamlesh.com/cubewright)

Have a Minecraft mod idea? **CubeWright AI** is our upcoming AI mod-building
assistant. Describe the blocks, items, mobs, commands, menus, or mechanics you
want, and it will help create a structured Minecraft mod project.

**Coming soon — click the banner to follow the launch.**
```

# ✦ Super Mod Menu

A focused, modern mod menu for Fabric (Minecraft 1.21.4). It does a few things that the
default Mod Menu doesn't — and tries to do them well, without clutter.

## Features

| Feature | Description |
|---|---|
| **In-game mod browser** | Search & install Fabric mods from Modrinth without leaving the game (`⬇ Get Mods`). |
| **Update checker** | Hashes your installed JARs and asks Modrinth which ones have a newer version. |
| **Rich detail panel** | Real Modrinth descriptions and icons (incl. WebP), version, and authors — cached per session. |
| **Favorites** | Star mods to pin them to the top of the list. |
| **Notes** | Attach a personal note to any mod (right-click a row, or `✎ Note`). |
| **Dependency graph** | See what a mod depends on and what depends on it. |
| **Config integration** | If Mod Menu is installed, `⚙ Configure` opens the mod's own config screen. |
| **Open on Modrinth** | Jump straight to the mod's Modrinth page. |

The list shows only top-level, user-installed mods. Bundled libraries and nested
(jar-in-jar) modules are hidden automatically.

## Building

Requires Java 21 (wrapper included).

```bash
./gradlew clean build
```

Output JAR: `build/libs/supermodmenu-1.0.0.jar`.

## Installing

Drop the JAR into `.minecraft/mods/` alongside Fabric Loader and Fabric API.

## Usage

- **Title screen** (bottom-right) or **Pause menu**: `✦ Mods (N)`
- **Left-click** a mod to see details
- **Right-click** a mod to edit its note
- `⟳ Check Updates` to query Modrinth for newer versions
- `⬇ Get Mods` to browse and install from Modrinth

## Notes

- WebP project icons are transcoded to PNG via the public `wsrv.nl` image proxy
  (Minecraft's image decoder doesn't support WebP). If it's unreachable, a letter
  placeholder is shown instead.

## Attribution

Mod data, search, and downloads are powered by the [Modrinth](https://modrinth.com) API.

## Author

Built by **Kamlesh Choudhary** ([@devxkamlesh](https://x.com/devxkamlesh))
· [devxkamlesh.com](https://devxkamlesh.com)
· [GitHub](https://github.com/devxkamlesh)
· [LinkedIn](https://linkedin.com/in/devxkamlesh)

## License

MIT

# Minecraft 26.2 Upgrade

Version 1.2.0 is compiled directly against Minecraft 26.2 and its unobfuscated API.
It is not merely marked compatible: GUI rendering, input events, textures, screen
navigation, Fabric dependencies, Gradle, and Java were migrated for 26.2.

## How to Build the Fixed Version

### Option 1: Use the batch file (Java 25 required)
```cmd
build-with-java21.bat
```

### Option 2: Manual build
```cmd
gradlew.bat clean build
```

**Output**: `build\libs\supermodmenu-1.2.0.jar`

## After Building

1. Remove the old Super Mod Menu JAR.
2. Add `supermodmenu-1.2.0.jar`.
3. Launch Minecraft 26.2 - it will now work! ✅

## Technical Details

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.158.0+26.2
- Fabric Loom 1.17
- Gradle 9.5.1
- Java 25

The metadata intentionally declares exactly Minecraft 26.2. Future versions must be
compiled and tested rather than accepted through an unsafe wildcard.

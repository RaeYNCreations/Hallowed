# Hallowed

A Dark Souls-inspired death and resurrection mod for Minecraft 1.21.1+ (NeoForge).

## Overview

Hallowed replaces Minecraft's default death behavior with a persistent "Hallowed" ghost state. Dead players become spectral entities with limited world interaction, and must use Bonfires and coins (via Lightman's Currency) to resurrect.

## Dependencies

- **Lightman's Currency** (Required) — Coin-based economy for resurrection costs
- **Bonfires** (Required) — Bonfire interaction points for resurrection
- **You Died** (Optional) — Death overlay animation

## Building from Source — Obtaining Dependency JARs

The Lightman's Currency and Bonfires dependencies are declared as `compileOnly` entries
pointing to the local `libs/` folder. Because these JARs are not distributed with this
repository, you must obtain them manually before compiling.

1. **Create the `libs/` directory** in the repository root (if it doesn't exist):
   ```
   mkdir libs
   ```

2. **Download Lightman's Currency** for NeoForge 1.21.1 from:
   - CurseForge: <https://www.curseforge.com/minecraft/mc-mods/lightmans-currency>
   - Modrinth: <https://modrinth.com/mod/lightmans-currency>

3. **Download Bonfires** for NeoForge 1.21.1 from:
   - CurseForge: <https://www.curseforge.com/minecraft/mc-mods/bonfires>
   - Modrinth: <https://modrinth.com/mod/bonfires>

4. *(Optional)* **Download You Died** for NeoForge 1.21.1 and add it to `libs/` as well.

5. **Place all JARs** in the `libs/` folder. The build picks up `*.jar` from that folder
   automatically via `compileOnly fileTree(dir: 'libs', include: ['*.jar'])`.

> **Note on package names:** `BonfireHelper.java` references
> `com.mango.bonfires.block.entity.BonfireTileEntity`. If the version of the Bonfires
> mod you are using has a different package or class name, update the import and
> `instanceof` checks in that file accordingly.

## Status

🚧 Under Development

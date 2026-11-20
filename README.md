<div align="center">
  <img src="public/capybara.gif" alt="Capybara Logo" width="180" />
  <h1>Capybara</h1>
  <p><strong>Low-level item scanner and duplication detection — actively powering <a href="https://github.com/LUCKYVN-NETWORK">LuckyVN</a>'s administrators</strong></p>

  [![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4%20%E2%80%93%201.21.11-0284c7?style=flat-square)](https://www.minecraft.net/)
  [![Java](https://img.shields.io/badge/Java-21-e11d48?style=flat-square)](https://adoptium.net/)
  [![Maven](https://img.shields.io/badge/Maven-3.9-c71a36?style=flat-square)](https://maven.apache.org/)
  [![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Folia-16a34a?style=flat-square)](https://papermc.io/)

  <sub><em>Logo: AI-generated.</em></sub>
</div>

---

**Capybara** is a low-level item scanner and duplication detector for Paper and Folia servers. It reads player data,
ender chests, PlayerVaults, zAuctionHouse listings and region files straight from storage through version-specific NMS
adapters. With it you can locate any item across the whole server, tally everything that is stored, and flag
suspicious duplication as it happens.

## Layout

| Module | Artifact | Contents |
|---|---|---|
| `/` | `capybara-parent` | Parent/aggregator POM |
| `cook-nms/` | `capybara-cook-nms` | Cooks NMS binaries from server distributions (`Cook.java`) |
| `core/` | `capybara-core` | Version-independent plugin code + `CapybaraNative` contract |
| `nms/v1_21_4/` | `capybara-nms-1.21.4` | `Capybara2104` ← `libs/nms/leaf-1.21.4.jar` |
| `nms/v1_21_8/` | `capybara-nms-1.21.8` | `Capybara2108` ← `cook-nms/nms/leaf-1.21.8.jar` (cooked from `libs/dist-server/leaf-1.21.8-175.jar`) |
| `nms/v1_21_11/` | `capybara-nms-1.21.11` | `Capybara2111` ← `libs/nms/leaf-1.21.11.jar` |
| `nms/v1_21_11_folia/` | `capybara-nms-1.21.11-folia` | `Capybara2111Folia` ← `libs/nms/canvas-1.21.11.jar` |
| `global/` | `capybara` | Links core + all NMS modules into `global/target/Capybara-2026.11.jar` |

## Build

Requires JDK 21+ and network access (Maven dependencies; the first cook downloads the vanilla server from Mojang).

```
mvn package
```

`cook-nms` runs first in the reactor. For every Paperclip/Leaper bundle in `libs/dist-server/` and `libs/nms/`, it starts a
server instance in `cook-nms/instances/<bundle>/` with `-Dpaperclip.patchonly=true`. The launcher patches the server
and exits, so no EULA has to be accepted. The binary in `versions/` is then checked against the bundle's checksum and
copied to `cook-nms/nms/`. Jars that are already cooked are skipped, and so are outputs that are up to date.

- `-Dcook.skip=true` skips cooking, `-Dcook.force=true` re-cooks everything.
- Standalone: `java cook-nms/Cook.java libs/dist-server libs/nms` (run from `cook-nms/`).

## Commands

`/capybara` (permission `capybara.admin`, default op). Run `/capybara help` for the interactive board: hover an entry
for details, click it to fill it in.

| Sub-command | Permission | Description |
|---|---|---|
| `inv` · `ec` · `vault` · `ah` | `capybara.scan` | Find the held item in inventories / ender chests / PlayerVaults / zAuctionHouse |
| `world <world> [since]` | `capybara.scan` | Find the held item in containers and entities of a world (optional age filter: `1h`, `24h`, `7d`) |
| `home <radius>` | `capybara.scan` | Closest Essentials home / last location within the radius |
| `poll <inv\|ec\|vault\|ah\|world> [world] [since]` | `capybara.poll` | Tally every stored item of a region (console allowed) |
| `status` · `cancel` | `capybara.admin` | Inspect running scans or cancel your active scan |
| `nuke-chunk` · `nuke-container` | `capybara.nuke` | Empty containers |

Reports keep their established format and go to `plugins/Capybara/scans/` and `plugins/Capybara/polls/`.

## Adding a server version

1. Drop the cooked NMS jar into `libs/nms/`, or the server distribution into `libs/dist-server/`.
2. Copy an `nms/v*` module, then set its NMS `systemPath`, `paper.api.version` and `datafixerupper` version.
3. Register it in `nms/pom.xml` and as a dependency of `global/pom.xml`.
   The runtime picks `me.stella.nativelib.Capybara<minor*100+patch>[Folia]`.

## Third-party jars (`libs/compat/`, gitignored)

Place external plugin binaries in `libs/compat/` (gitignored so private/internal plugin jars are never leaked).
Compile-only (`system` scope, never shaded): `MMOItems-*.jar`, `MythicLib-dist-*.jar`, `PlayerVaultsX.jar` (4.4.11),
`zAuctionHouse-3.2.4.5.jar` (full plugin — provides `fr.maxlego08.zauctionhouse.ZAuctionPlugin`, absent from the public API
artifact). Jar names are POM properties in the root `pom.xml`. EssentialsX `2.21.2` resolves from repo.essentialsx.net.
`CrazyVouchers` / `ExcellentCrates` are not compile dependencies (Capybara only reads their PDC keys).

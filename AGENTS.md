# AGENTS.md — Capybara Agent Guidelines

Operational and architectural standards for AI coding agents working on **Capybara** (low-level item scanner and duplication detection for Paper & Folia `1.21.4–1.21.11`, built on **Java 21** + **Maven**).

---

## 1. Repository & Module Structure

```text
Capybara/
├── pom.xml                       # Parent / aggregator POM (JDK 21 enforced)
├── cook-nms/                     # Generates NMS jars from libs/dist-server/ bundles (Cook.java)
├── core/                         # Version-independent plugin code & CapybaraNative contract
├── nms/
│   ├── v1_21_4/                  # Capybara2104      <- libs/nms/leaf-1.21.4.jar
│   ├── v1_21_8/                  # Capybara2108      <- cook-nms/nms/leaf-1.21.8.jar
│   ├── v1_21_11/                 # Capybara2111      <- libs/nms/leaf-1.21.11.jar
│   └── v1_21_11_folia/           # Capybara2111Folia <- libs/nms/canvas-1.21.11.jar
├── global/                       # Shades core + all NMS modules -> global/target/Capybara-<ver>.jar
├── libs/
│   ├── nms/                      # Prebuilt NMS binaries
│   ├── dist-server/              # Server distribution bundles cooked by cook-nms/
│   └── compat/                   # External plugin binaries (gitignored; never commit)
└── public/                       # Project media assets (README logo)
```

---

## 2. Package & Code Structuring Rules

### 2.1 Clear Tree-Based Hierarchy
- Organize packages as a **clean, shallow-to-medium functional tree** where every branch represents a single responsibility.
- **Avoid flat class-only packages with 5+ classes.** Keep leaf packages to **at most 4 top-level classes** (excluding tiny sibling value records only if tightly bound). Once a package reaches **5 or more top-level `.java` files**, split it into cohesive child packages by role or feature rather than appending more flat classes.
- Place every **new** class directly into the right leaf package from the start.
- **Keep package moves isolated:** When refactoring an existing oversized flat package into sub-packages, perform the move in a dedicated `refactor(<target>): ...` commit containing only package/import/visibility updates—do not mix behavioral changes into structural moves.

### 2.2 Target Package Tree (`core/src/main/java/me/stella/`)

```text
me.stella/
├── Capybara.java                 # Plugin bootstrap & lifecycle
├── CapybaraNative.java           # Version-specific scanner contract
├── CapybaraScan.java             # Item ID resolution & scanner facade
│
├── command/                      # Annotation-driven command framework
│   ├── annotations/              # @CommandMeta, @SubCommandMeta
│   ├── dispatch/                 # AbstractCommand, DispatchingCommand, CommandDiscovery, SubCommandRegistry
│   ├── suggestion/               # SubCommandHandler, SuggestionProvider, Suggestions
│   └── capybara/                 # /capybara root & sub-commands (grouped by domain)
│       ├── CapybaraCommand.java  # Root dispatcher
│       ├── StatusCommand.java    # Operational status
│       ├── CancelCommand.java    # Cooperative scan cancellation
│       ├── PollCommand.java      # Full-region polling
│       ├── scan/                 # HeldItemScanCommand, WorldScanCommand, HomeScanCommand, AuctionScanCommand
│       │   └── player/           # InventoryScanCommand, EnderScanCommand, VaultScanCommand
│       └── nuke/                 # NukeChunkCommand, NukeContainerCommand
│
├── scan/                         # Async scan lifecycle, Anvil/NBT streaming & live progress
│   ├── model/                    # ScanJob, ScanSession, ScanProgress
│   ├── anvil/                    # AnvilRegionReader, StreamingNbtScanner, RawItem, RawItemMatcher
│   ├── engine/                   # ScanExecutors, ScanService, WorldScanEngine, PlayerDataScanEngine
│   └── monitor/                  # ScanMonitor, ScanRenderer
│
├── duplication/                  # Real-time duplication detection
│   ├── config/                   # DuplicationDetectionConfig
│   ├── snapshot/                 # InventorySnapshot, InventorySnapshotFactory
│   ├── engine/                   # DuplicationDetectionModule, DuplicationDetectionListener, DuplicationScanTask
│   └── alert/                    # SuspicionRecord, DiscordWebhook
│
├── ui/                           # Adventure UI helpers (Theme, Messages, Format)
└── utility/                      # Shared low-level utilities (CapybaraIO, Reports, MutableOptional)
```

> **Note:** Existing packages that currently exceed the 5-class threshold (`command/`, `command/capybara/`, `scan/`, `duplication/`) should be split along the branches above when touched by a structural refactor, and **no new classes** should be added flat into them.

### 2.3 Module & Class Boundaries
- **`core` vs. `nms/v*`:** Keep all version-independent logic (command routing, scheduling, UI, report I/O, Anvil `.mca` framing, raw-NBT scanning) in `core`. Each `nms/v*` module should contain only its version-specific `me.stella.nativelib.Capybara<minor*100+patch>[Folia]` adapter and minimal helpers.
- **Command Discovery Contract:** Root commands use `@CommandMeta` and extend `AbstractCommand`/`DispatchingCommand`; sub-commands use `@SubCommandMeta(root = "capybara", ...)` and implement `SubCommandHandler` with a no-arg constructor. `CommandDiscovery` recursively scans `me.stella.command` and its sub-packages, so sub-package grouping works automatically without manual registration.
- **Naming & Conciseness:** Prefer clear, domain-specific names over generic `Manager`/`Helper`/`Util` dumping grounds. Keep classes focused on one responsibility; split large classes before adding unrelated helpers.

---

## 3. Commit Message Convention

Every commit **must** follow the format:

```text
<changetype>(<target>): <message>
```

### 3.1 Allowed Change Types
| Type | When to use |
|---|---|
| `feat` | New user-facing feature, sub-command, scanner capability, or NMS version module |
| `fix` | Bug fix (correctness, concurrency, crash, reflection, resource leak) |
| `perf` | Performance, memory, GC, or I/O optimization without changing behavior |
| `refactor` | Structural code or package reorganization with no behavior change |
| `build` | Maven POMs, `cook-nms`, shading, dependencies, or toolchain changes |
| `docs` | `README.md`, `AGENTS.md`, or documentation-only edits |
| `test` | Adding or updating unit/integration/benchmark tests |
| `chore` | Maintenance tasks (`.gitignore`, housekeeping, version bumps) |
| `style` | Formatting, whitespace, or lint cleanup only |

### 3.2 Target Scopes
Use a concise, lowercase module or subsystem name as `<target>`:
- **Core subsystems:** `core`, `scan`, `command`, `duplication`, `ui`, `io`
- **NMS & build:** `nms`, `nms-1.21.4`, `nms-1.21.8`, `nms-1.21.11`, `nms-1.21.11-folia`, `cook-nms`, `global`, `deps`, `build`
- **Repo-wide:** `docs`, `repo`

### 3.3 Rules & Examples
- Use **imperative present tense** ("add", "fix", "split", not "added", "fixes", "splitting").
- Start `<message>` in **lowercase** with **no trailing period**.
- Keep the subject line **<= 72 characters**.
- When context is non-obvious, add a blank line followed by a short body explaining **why** the change was made (not a file-by-file diff summary).

**Good:**
```text
feat(command): add /capybara cancel sub-command for active scans
fix(nms): remove double bit-shift on ChunkPos for external .mcc chunks
perf(scan): stream block_entities via StreamTagVisitor to skip chunk sections
refactor(command): split capybara sub-commands into scan and nuke packages
build(deps): wire local PlayerVaultsX and zAuctionHouseV3 jars
docs(repo): add AGENTS.md structuring and commit conventions
```

**Avoid:**
```text
Updated scanner stuff.                     # missing type(target), past tense, trailing period
fix: fixed bug in world scan               # missing target, vague
feat(core,scan,nms,ui): big rewrite        # overly broad target; split into focused commits
```

---

## 4. Engineering & Runtime Standards

- **Paper & Folia Safety:** Never assume a single main thread. Schedule player UI updates on `player.getScheduler()` and console/background ticks on `Bukkit.getAsyncScheduler()`. Never mutate Bukkit/NMS world state off-thread.
- **Zero Main-Thread Stalls:** Do not run disk I/O, heavy deserialization, or `O(N)` collection iterations on tick/entity threads. Use `O(1)` atomic counters (`LongAdder`) for live progress sampling and never call `System.gc()`.
- **Isolated Executors:** Do not mix blocking parent/coordinator tasks and child worker tasks in the same fixed pool, do not hijack `ForkJoinPool.commonPool()` for disk I/O, and isolate real-time duplication detection from bulk world scans.
- **Read-Only Storage Access:** World/playerdata scanners must open storage files read-only and handle torn reads gracefully rather than mutating live server files.
- **Adventure API:** Use Adventure `Component` / MiniMessage (`me.stella.ui.Messages` + `Theme`) for all player-facing chat and action-bar output. Always pass dynamic/user strings via `Placeholder.unparsed(...)`. Avoid deprecated `ChatColor` in new code.
- **Git Hygiene:** Never commit `target/`, `cook-nms/instances/`, `cook-nms/nms/`, `libs/compat/`, `*.iml`, `.idea/`, or temporary scratch files.

---

## 5. Build & Verification

- **Toolchain:** JDK 21+ (`JAVA_HOME`) and Maven 3.6.3+ (IntelliJ's bundled Maven 3.9 works).
- **Full build:**
  ```bash
  mvn -B clean package
  ```
  - Pass `-Dcook.skip=true` when NMS binaries in `cook-nms/nms/` are already up to date.
  - Pass `-Dcook.force=true` to force re-cooking from `libs/dist-server/`.
- **Verification checklist before concluding any code change:**
  1. `mvn -B clean package` succeeds across all 9 modules with zero warnings/errors.
  2. Shaded output exists at `global/target/Capybara-<version>.jar` with `Main-Class: me.stella.Capybara` and all expected `me.stella.nativelib.Capybara*` classes intact.
  3. Any newly added package stays within the `< 5` flat-class rule and follows the tree structure in §2.2.

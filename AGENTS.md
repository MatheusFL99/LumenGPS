# AGENTS.md

Guidance for AI agents working in the LumenGPS repository.

## What this is

A **client-side Fabric mod** for Minecraft 26.1.2 that adds GPS waypoints and an A*-computed glowing particle trail. Works on any server (Vanilla, Spigot, Paper) without the server installing the mod.

`fabric.mod.json` declares `"environment": "client"` and has two entrypoints: `com.lumengps.LumenGPS` (common — currently only sets `MOD_ID` and the logger) and `com.lumengps.LumenGPSClient` (where the real work is registered: commands, ticks, chat listener, world join/disconnect).

## Toolchain (non-obvious)

- **Java 25 JDK** required. `build.gradle:16` pins `JavaLanguageVersion.of(25)`. Java 21+ features (records, switch expressions, virtual threads) are already in use — keep the toolchain at 25.
- **Gradle 9.5.0** via the wrapper (`gradlew.bat`). Gradle 8.x will not work; 9.x is required.
- **Minecraft 26.1.2 is unobfuscated.** This means:
  - The standard `jar` task is used, **not** `remapJar` (`build.gradle:36`).
  - There is no `mappings` block, no `yarn`, no `intermediary` — `minecraft` is a plain `implementation` dep.
  - Adding a Yarn/Intermediary layer would break the build. Don't.
- Fabric Loom `1.17.0-alpha.8` (preview line because MC 26.1.x is in pre-release naming).
- `gradle.properties` is the single source of truth for versions; bump there, not in `build.gradle`.

## Build & run (Windows / PowerShell)

Use the wrapper from the repo root, not a globally installed `gradle`:

```powershell
.\gradlew.bat build       # produces build/libs/lumengps-<version>.jar
.\gradlew.bat runClient   # launches test MC client with the mod injected
.\gradlew.bat genSources  # generate IDE sources for VS Code / IntelliJ
```

VS Code tasks (defined in `.vscode/tasks.json`):
- `LumenGPS: Build` — default build (`Ctrl+Shift+B`)
- `LumenGPS: Watch` — runs `build --continuous`; rebuilds on every `.java` save. Background task.

A `Minecraft Client` and `Minecraft Server` launch config also exist in `.vscode/launch.json`, but the `launch.json` file is `.gitignore`d — do not commit changes to it; it's a personal local config.

## Source layout

All under `com.lumengps` (note the spelling, not `lumengps`):

```
src/main/java/com/lumengps/
  LumenGPS.java          # common entry (ModInitializer) — currently a no-op besides logging
  LumenGPSClient.java    # client entry (ClientModInitializer) — wires everything up
  command/GpsCommand.java        # /gps Brigadier tree
  data/Waypoint.java             # record: pos, dimension, style
  data/WaypointManager.java      # singleton; per-world JSON persistence
  data/GpsConfig.java            # singleton; mod config (intelligentMode, allowWater, …)
  gui/GpsConfigScreen.java       # config UI (opened via /gps config)
  pathfinding/Pathfinder.java    # async A* (Virtual Thread) with crow-fly fallback
  pathfinding/PathNode.java
  pathfinding/PathResult.java
  renderer/GpsRenderer.java      # singleton; per-tick particle spawning
  renderer/GpsHud.java
  util/BlockUtil.java            # block walkability / climbability / fluid checks
src/main/resources/
  fabric.mod.json
  lumengps.mixins.json           # empty mixin arrays, but package + JAVA_25 declared
  assets/lumengps/lang/{en_us,pt_br}.json
```

## Two distinct config locations (easy to confuse)

The mod writes to **two different paths** and they have different shapes:

1. **Mod config** → `<minecraft>/config/lumengps.json`
   Written/read by `GpsConfig` (`GpsConfig.java:17`). Holds flags: `intelligentMode`, `allowWater`, `allowLava`, `enableDeathWaypoint`, `enableLightPillar`, `requireCompass`, `showHud`. Uses Fabric's config dir.
2. **Per-world waypoints** → `<minecraft>/config/lumengps/waypoints_<worldId>.json`
   Written/read by `WaypointManager` (`WaypointManager.java:52,125`). One file **per world/server** — see "Multi-world storage" below. Subdirectory, not the same file as the mod config.

`GpsConfig.getInstance()` lazy-loads; `WaypointManager.getInstance()` is a true singleton built at class load.

## Multi-world storage (regression-prone)

Waypoints are **not** stored in a single `waypoints.json`. The world id is computed in `LumenGPSClient.getWorldId` (`LumenGPSClient.java:150`):

- Multiplayer: `multiplayer_<server_ip>` (colons → underscores, non `[a-zA-Z0-9_-]` stripped).
- Singleplayer: `singleplayer_<level_name>` (sanitized to `[a-zA-Z0-9_-]`).
- Unknown / pre-join: `unknown`.

`WaypointManager.load(worldId)` is called from `ClientPlayConnectionEvents.JOIN` (`LumenGPSClient.java:74`); `clear()` is called on `DISCONNECT`. If you change how the world id is computed you will **silently lose access to existing waypoints** (a new file is created and the old one becomes orphaned in `config/lumengps/`).

## `/gps` command quirks

- `/gps <name>` is a shortcut for `/gps go <name>` (`GpsCommand.java:64`). The shortcut suggestions **must filter out the sub-command keywords** (`help, add, addpos, go, remove, remove_confirm, share, clear, list`) so they don't shadow real commands — see filter at `GpsCommand.java:67`.
- `/gps remove <name>` does **not** delete; it prints an inline confirmation with `[✓ Yes]` / `[✗ Cancel]` buttons. The actual delete is triggered by the internal `/gps remove_confirm <name>` (`GpsCommand.java:220`). Do not collapse these or you break the safety prompt.
- `/gps share <name>` sends a **plain-text** public chat message — no `§` color codes, no rich components. The format is parsed by a regex in `LumenGPSClient.java:50`:
  `\[LumenGPS\] Shared Waypoint: '(.*?)' at (-?\d+), (-?\d+), (-?\d+) \(Style: (.*?)\)`
  If you change that format, update both the sender in `GpsCommand.navigateTo`/`buildShareFeedback` and the listener regex in `LumenGPSClient`.
- Dimension mismatch (`/gps go` to a waypoint in a different dimension) returns an error rather than computing a path — see `GpsCommand.navigateTo`.
- Death waypoint (`enableDeathWaypoint`): auto-saves a waypoint named `death` with style `soul` at the player's position when health drops to 0 — see tick handler in `LumenGPSClient.java:85`. It uses the `lastHealth` / `lastWasElytra` static state in that file.
- Elytra auto-switch: when the chest-slot elytra state changes mid-route, the renderer swaps the active style (`glow` ↔ `end`) and triggers a re-`computeAsync`. A 40-tick (2s) cooldown (`LumenGPSClient.java:141`) prevents thread spam.
- `requireCompass` config flag: when true, `/gps go` requires a compass in main or off-hand (`GpsCommand.java:311`). The `goCommand` helper at `GpsCommand.java:304` is currently unused (the `then` branch inlines its own version) — leaving it dead but not removing; do not delete the helper without checking it isn't called elsewhere.

## Pathfinder contract

`Pathfinder.computeAsync(world, start, goal, isElytraMode, callback)` (`Pathfinder.java:56`):

- Runs on a **Java 25 virtual thread** (`Thread.ofVirtual().name("lumengps-pathfinder").start(...)`). The main client thread is never blocked.
- **Callback contract:** the consumer is invoked via `Minecraft.getInstance().execute(...)`, so it always runs on the client main thread. Callers can safely touch `Minecraft`/`GpsRenderer` from inside the callback. Do **not** touch `Minecraft` or the renderer from the calling thread before the callback fires.
- Safety guards: `MAX_NODES = 100_000`, `MAX_TIME_MS = 3_000`. The deadline check is throttled with `& 0xFF` (`Pathfinder.java:91`) — leaving that mask in place matters for performance.
- Crow-fly fallback (`Pathfinder.java:140`): if A* fails or is bounded out, returns a straight elevated line at `max(start.y, goal.y) + 8.0`. `PathResult.isFallback()` is the flag callers use to switch the user-facing message.
- The cost function (`calculateStepCost`, `Pathfinder.java:198`) reads `GpsConfig.getInstance()` on every node — that's intentional, do not cache the config inside the loop.

## No tests, no CI

- There is no `src/test/` directory and no testing framework dependency in `build.gradle`. Do not invent one without asking.
- There is no `.github/workflows/` and no CI configuration. Don't assume `mvn test` / `gradle test` exists; the only meaningful verification is `./gradlew.bat build` and `./gradlew.bat runClient`.

## Build artifacts & git

- `build/`, `.gradle/`, `run/`, `bin/`, `out/`, `.idea/`, `*.iml`, `.vscode/launch.json`, `features-futuras.md` are all gitignored. A stale `bin/` with Eclipse-style compiled `.class` files may already be in the working tree — leave it alone, it's not source.
- `build.gradle:37` tries to bundle a `LICENSE` file into the jar, but **no `LICENSE` file exists in the repo**. `fabric.mod.json` declares `MIT`. Either add the missing file or remove the `from('LICENSE')` block — leaving it as-is produces a silent no-op `from`.
- `processResources` (`build.gradle:47`) substitutes `${version}` into `fabric.mod.json`. Don't hardcode a version there.

## Style / conventions observed

- Encoding is `UTF-8` (`build.gradle:44`); preserve it for any new resource files.
- `lumengps.mixins.json` declares `compatibilityLevel: "JAVA_25"` even though both `mixins` and `client` arrays are empty — leave the package/compatibility level alone if you add a mixin later.
- Lang keys live under `lumengps.command.*`, `lumengps.command.list.*`, `lumengps.command.share.*`, `lumengps.command.help.*`, etc. New user-facing strings should go to both `en_us.json` and `pt_br.json`.
- `Waypoint` names are case-insensitive and stored lowercased (`WaypointManager.java:78`). The dimension string uses the full `minecraft:overworld` / `minecraft:the_nether` / `minecraft:the_end` form; `GpsCommand.formatDim` handles the human-readable shortening.

# AGENTS.md

Guidance for AI agents working in the LumenGPS repository.

## What this is

A **server-side Fabric mod** for Minecraft 26.2 that adds GPS waypoints and an A*-computed glowing particle trail. Works on any server (Vanilla, Spigot, Paper) with client vanilla players (no client installation required).

`fabric.mod.json` declares `"environment": "*"` and has one entrypoint: `com.lumengps.LumenGPS` (common ModInitializer — registers commands, server tick event `ServerTickEvents.END_SERVER_TICK`, and handles player disconnect events).

## Toolchain (non-obvious)

- **Java 25 JDK** required. `build.gradle:16` pins `JavaLanguageVersion.of(25)`. Java 21+ features (records, switch expressions, virtual threads) are already in use — keep the toolchain at 25.
- **Gradle 9.5.0** via the wrapper (`gradlew.bat`). Gradle 8.x will not work; 9.x is required.
- **Minecraft 26.2 is unobfuscated.** This means:
  - The standard `jar` task is used, **not** `remapJar` (`build.gradle:36`).
  - There is no `mappings` block, no `yarn`, no `intermediary` — `minecraft` is a plain `implementation` dep.
  - Adding a Yarn/Intermediary layer would break the build. Don't.
- Fabric Loom `1.17.0-alpha.8`.
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
  LumenGPS.java          # common entry (ModInitializer) — registers commands, ticks and disconnect events
  ServerGpsManager.java  # singleton; manages active routes (static + player tracking) and triggers per-tick particle rendering
  command/GpsCommand.java        # /gps Brigadier tree (handles scopes and options)
  data/Waypoint.java             # record: pos, dimension, style
  data/WaypointManager.java      # per-player UUID JSON persistence (get/unload by UUID)
  data/ServerWaypointManager.java # singleton; global server waypoints persistence
  data/GpsConfig.java            # singleton; mod config (intelligentMode, allowWater, …)
  pathfinding/Pathfinder.java    # async A* (Virtual Thread) with crow-fly fallback/continuation
  pathfinding/PathNode.java
  pathfinding/PathResult.java
  util/BlockUtil.java            # block walkability / climbability / fluid checks
  util/OfflineChunkBlockGetter.java # implements BlockGetter for async chunk reading from .mca files
src/main/resources/
  fabric.mod.json
  lumengps.mixins.json           # empty mixin arrays, compatibilityLevel: JAVA_25
  assets/lumengps/lang/{en_us,pt_br}.json
```

## Config & persistence locations

The mod writes to **three different paths**:

1. **Mod config** → `config/lumengps.json`
   Written/read by `GpsConfig` (`GpsConfig.java:17`). Holds flags: `intelligentMode`, `allowWater`, `allowLava`, `enableDeathWaypoint`, `enableLightPillar`, `requireCompass`, `showHud`, `confirmOverwrite`, `enablePlayerTracking`. Uses Fabric's config dir.
2. **Per-player waypoints** → `config/lumengps/waypoints/<player_uuid>.json`
   Written/read by `WaypointManager`. One file **per player UUID** — `WaypointManager.get(uuid)` returns the manager, `WaypointManager.unload(uuid)` frees it on disconnect. The `gui/` and `renderer/` directories exist but are empty placeholders.
3. **Server waypoints (Global)** → `config/lumengps/server_waypoints.json`
   Written/read by `ServerWaypointManager`. Holds server-wide waypoints, managed via `/gps server` subcommands.

## `/gps` command quirks

- `/gps <name>` is a shortcut for `/gps go <name>`. The shortcut suggestions **must filter out the sub-command keywords** (`help, add, addcord, add_overwrite, add_overwrite_cancel, go, remove, remove_confirm, share, clear, list, server, config`) so they don't shadow real commands.
- `/gps remove <name>` does **not** delete; it prints an inline confirmation with `[Sim]` / `[Cancelar]` buttons. The actual delete is triggered by the internal `/gps remove_confirm <name>` (`GpsCommand.java`). Do not collapse these or you break the safety prompt.
- `/gps share <name>` sends a **rich chat message** with clickable `[Adicionar]` button. Uses `Component.literal` with `§` color codes and `ClickEvent.RunCommand` to let other players click to add the shared waypoint. Format is broadcast via `broadcastSystemMessage`.
- `/gps go <name>` and `/gps share <name>` support a `scope` argument (`personal` or `server`). When both a personal and server waypoint share the same name and no scope is given, the player is prompted to choose.
- `/gps server` subcommands (add, addcord, add_overwrite, remove, remove_confirm, list) manage global server waypoints. `add*` and `remove*` require op (`Permissions.COMMANDS_GAMEMASTER`).
- Dimension mismatch (`/gps go` to a waypoint in a different dimension) returns an error rather than computing a path.
- Death waypoint (`enableDeathWaypoint`): auto-saves a waypoint named `death` with style `soul` at the player's position when health drops to 0.
- `requireCompass` config flag: when true, `/gps go` requires a compass in main or off-hand.
- `confirmOverwrite` config flag: when true (default), adding a waypoint with an existing name prompts the user to confirm before overwriting.
- `/gps follow <player>` tracks another online player in real-time. The path recalculates every 5 seconds. If the tracked player goes offline or changes dimension, the route is cancelled with a notification. Tab-completion lists online players (excluding self). Requires `enablePlayerTracking` config to be true. Uses a tighter arrival radius (3 blocks vs 7 for static waypoints).

## Pathfinder contract

`Pathfinder.computeAsync(ServerLevel world, BlockPos start, BlockPos goal, boolean isElytraMode, Consumer<PathResult> callback)` (`Pathfinder.java`):

- Runs on a **Java 25 virtual thread** (`Thread.ofVirtual().name("lumengps-pathfinder").start(...)`). The main server tick thread is never blocked.
- **Callback contract:** the consumer is invoked via `server.execute(...)`, so it always runs on the server main thread. Callers can safely touch world/entities from inside the callback. Do **not** touch them from the calling thread before the callback fires.
- Safety guards: `MAX_NODES = 400_000`, `MAX_TIME_MS = 8_000`. The deadline check is throttled with `& 0xFF` — leaving that mask in place matters for performance.
- `HEURISTIC_WEIGHT = 3.0` — weighted A* multiplier makes the search greedier (faster, less optimal path). Significant for understanding path behavior.
- Crow-fly fallback (`Pathfinder.java`): if A* fails or is bounded out, returns a straight elevated line at `max(start.y, goal.y) + 8.0` or appends a crow-fly path starting from the last computed node.
- The pathfinder wraps the `ServerLevel` in `OfflineChunkBlockGetter` so it can read block states from chunks not currently loaded in memory, by reading `.mca` region files on disk.

## No tests, no CI

- There is no `src/test/` directory and no testing framework dependency in `build.gradle`. Do not invent one without asking.
- There is no `.github/workflows/` and no CI configuration. The only meaningful verification is `./gradlew.bat build` and `./gradlew.bat runClient`.

## Build artifacts & git

- `build/`, `.gradle/`, `run/`, `bin/`, `out/`, `.idea/`, `*.iml`, `.vscode/launch.json`, `features-futuras.md` are all gitignored.
- `processResources` (`build.gradle:47`) substitutes `${version}` into `fabric.mod.json`. Don't hardcode a version there.

## Style / conventions observed

- Encoding is `UTF-8` (`build.gradle:44`); preserve it for any new resource files.
- Lang keys live under `lumengps.command.*`, `lumengps.command.list.*`, `lumengps.command.share.*`, `lumengps.command.help.*`, etc. New user-facing strings should go to both `en_us.json` and `pt_br.json`.
- `Waypoint` names are case-insensitive and stored lowercased. The dimension string uses the full `minecraft:overworld` / `minecraft:the_nether` / `minecraft:the_end` form; `GpsCommand.formatDim` handles the human-readable shortening.
- Particle styles are selected by string key in `ServerGpsManager`: `fire`, `soul`, `end`, `emerald`, default `glow`.

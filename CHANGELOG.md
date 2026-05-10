# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Help Menu**: Typing `/gps` or `/gps help` now displays a quick reference guide in chat for all available commands.
- **Visual Styles**: Added optional `[style]` parameter to `/gps add`. Supported styles are `glow`, `fire`, `soul`, `end`, and `emerald`.
- **Auto-Death Waypoint**: The mod automatically saves a waypoint named `death` (with the `soul` fire style) when the player dies.
- **Remove Command**: Added `/gps remove <name>` to delete saved waypoints via chat.
- **Internationalization (i18n)**: Fully integrated `en_us` and `pt_br` language files for all in-game messages.
- **Crow-fly Fallback**: The A* pathfinder now generates a straight elevated line if a walkable path cannot be found within the node/time limits.
- **Advanced Terrain Support**: Pathfinding now supports swimming, walking on bottom-slabs, stepping up to 2 blocks, and jumping down up to 3 blocks.

### Fixed
- Fixed an issue where the pathfinder would crash the client by hitting excessive node limits (capped at 100k nodes and 3 seconds).
- Fixed walkability checks that previously rejected transparent blocks and fluids.

# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/).

## [1.1.1] - 2026-07-14

### Added
- **Surface** display mode: semi-transparent white exterior faces for brush preview
- `HandlerRegistry` for centralized terrain tool handler management
- `TerrainToolHandler` interface with default method stubs
- Unit tests for handler registry and undo/redo concurrency

### Changed
- Refactored handler registration from static fields to `HandlerRegistry`
- Surface preview uses greedy mesh merging (adapted from Director)
- Updated preview display mode cycle: Wireframe → Point Cloud → Surface → None

### Fixed
- Invalid Unicode quotation marks in `UndoRedoManager` causing compile errors
- Missing `TerrainToolHandler` interface referenced by registry

## [1.1.0] - 2026

### Added
- Minecraft 1.21.11 support
- Nested config structure (`brush`, `surface`, `planting`, `shoreline`, `preview`)
- Legacy config migration from flat JSON structure (same `pushdozer_config.json` file)
- GameTest coverage for undo sync behavior
- Batch block operations with cross-tick scheduling for large edits

### Changed
- Java 21 required
- Fabric Loader ≥ 0.18.2

[1.1.1]: https://github.com/Theopote/pushdozer/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/Theopote/pushdozer/releases/tag/v1.1.0

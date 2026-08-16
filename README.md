# Val0x2F

> **A client-side Fabric optimization foundation for constrained devices.**
>
> Val0x2F is a Minecraft Fabric mod built around a persistent, low-allocation chunk-cache pipeline and strict background-work budgeting. Its immediate goal is to avoid adding stutter on mobile-class hardware while providing a foundation for future cache-backed rendering research.

**Current release:** [`v0.6.1-alpha`](https://github.com/Zaineedyou/Val0x2F/releases/latest)  
**Minecraft:** `1.21.11` · **Loader:** Fabric · **Environment:** Client only

## What Val0x2F does today

Val0x2F is currently a **cache-only alpha**. It does **not** add far LOD terrain, fake chunks, render-distance bypass, shader support, or a new terrain renderer in this release.

| Feature | Current behavior |
|---|---|
| Persistent chunk cache | Captures lightweight client chunk surface data and stores it in a compact journal cache, separated by world/server and dimension. |
| Safe capture timing | Queues cache capture for chunk unload rather than performing a full snapshot during chunk-load bursts. |
| Bounded background work | Uses dedicated, bounded cache and I/O workers so cache work does not create an unbounded thread pool. |
| Batched cache writes | Writes cache records asynchronously in batches instead of creating one file per chunk or forcing the storage device for each entry. |
| Memory-aware data path | Uses primitive arrays, snapshot reuse, bounded pools, revision invalidation, and CRC-protected cache records to reduce allocation pressure. |
| Low-FPS cache governor | When client FPS falls below 35, cache capture yields aggressively so rendering, Sodium, networking, and other game work get priority. |
| Particle suppression | Disables particle creation through a narrow client-side hook to reduce particle work on constrained devices. |
| Compatibility gates | Detects common optimization/render mods before enabling optional Val0x2F behavior; it does not replace their terrain renderer. |

## Why this mod exists

Minecraft Java on Android launchers and low-power hardware can suffer from frame-time spikes when chunk streaming, background work, storage writes, and garbage collection overlap. Val0x2F focuses on making its own pipeline predictable:

- **No per-chunk cache files.** Cache data is journaled instead of creating a directory full of tiny files.
- **No synchronous cache I/O on the render thread.** Disk work is batched and handled off-thread.
- **No runaway cache workload.** The cache backs off when the client is already below the FPS budget.
- **No forced video settings.** Val0x2F does not force render distance, simulation distance, VSync, frame limit, or graphics presets.

## Installation

1. Install **Fabric Loader** for Minecraft `1.21.11`.
2. Install **Fabric API**.
3. Download `val0x2f-0.6.1-alpha.jar` from the [Releases page](https://github.com/Zaineedyou/Val0x2F/releases).
4. Place the JAR in the instance's `mods` folder.
5. Launch the game.

Val0x2F is client-side. A multiplayer server does **not** need to install it.

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | `1.21.11` |
| Fabric Loader | `0.19.3` or newer |
| Fabric API | Required |
| Java | `21` or newer |

## Compatibility

Val0x2F is intended to coexist with the following client optimizations. They are optional unless another mod requires them.

| Mod | Intended relationship |
|---|---|
| Sodium | Sodium remains responsible for normal terrain rendering and chunk meshing. |
| Lithium | Runs independently; Val0x2F does not alter server-side simulation optimizations. |
| C2ME | Val0x2F keeps cache capture client-side and avoids assuming server chunk threading. |
| Better Render Distance | Val0x2F does not modify its normal terrain culling path. |
| Entity Culling / More Culling | Val0x2F does not duplicate their active culling workers. |
| Cull Leaves | Val0x2F does not inject into leaf rendering. |
| Iris | No shader-specific renderer is included in the current cache-only release. |

## Important alpha limitations

This is an **alpha** project. Please use it on a copied profile/world first and keep your `latest.log` if you encounter an issue.

- This release does **not** render cached chunks as distant terrain.
- This release does **not** bypass the server render distance.
- The persistent cache is infrastructure for future work; it is not currently a large standalone FPS boost.
- Performance varies widely by launcher, Android driver, thermal state, resource pack, and installed modpack.

## Development direction

The long-term architecture under evaluation is a cache-backed, mobile-safe distant-terrain system. Any future renderer must first meet these requirements before it is released:

1. It must render correct terrain topography rather than debug geometry.
2. It must only use chunks already received by the client; it must not request or fabricate server data.
3. It must not force user video settings.
4. It must stay within strict CPU, GPU, native-memory, and garbage-collection budgets on constrained hardware.
5. It must be validated in real Android runtime environments before being described as usable.

## Reporting an issue

Please include the following when opening an issue:

- Minecraft, Fabric Loader, Fabric API, and Val0x2F versions.
- Device model, Android version, launcher, and renderer/driver selection.
- A complete `latest.log`.
- A Java Flight Recorder (`.jfr`) only if it is non-empty and was captured for the problem being reported.
- Clear steps to reproduce the issue.

## License

Copyright © 2026 Zaineedyou. **All Rights Reserved.**

No permission is granted to copy, modify, redistribute, or create derivative works without prior written permission from the copyright holder. See [`LICENSE`](LICENSE) for the full terms.

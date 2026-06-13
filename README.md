PlotMe
======

Plot management plugin for Paper 1.21+. Maintenance fork of the abandoned
WorldCretornica/PlotMe-Core, modernized for current Minecraft and merged
with the (also abandoned) PlotMe-DefaultGenerator so the whole plot system
ships as a single jar.

Status: **v1.0.2** — Paper 1.21.4 / Java 21. See [Releases](https://github.com/Melocet/PlotMe/releases) for the full changelog.

Features
--------
- All original `/plotme` commands + permissions, behavior preserved
- Built-in plot-world generator (no companion plugin needed)
- Chest-GUI menu (`/plotme menu`) with a Biome page
- Manual plot merging with configurable cluster cap + permission tiers
- `/plotme dispose` restores the road grid with exact chunk-gen pattern parity
- Per-plot flag system (`/plotme flag <name> [value]`)
- Web-map markers for **BlueMap** and **squaremap** (facade pattern — both
  hooks auto-disable cleanly when the backing plugin isn't installed)
- Colorized chat with `[PlotMe]` prefix; legacy plain-text mode via
  `use-legacy-texts: true`
- Colored Adventure signs on claimed and merged plots
- bStats metrics, shaded under `com.worldcretornica.plotme_core.libs.bstats`

Optional integrations (soft-deps)
---------------------------------
- **Vault** — economy hooks for claim/sell/biome/etc. pricing
- **WorldEdit 7** — WE-anywhere permission + per-plot edit restriction
- **BlueMap** — plot markers on the BlueMap web frontend
- **squaremap** — plot markers on the squaremap web frontend
- **Multiverse-Core** — easy plot-world creation

Setup
-----
1. Drop the jar from the [latest release](https://github.com/Melocet/PlotMe/releases/latest)
   into `plugins/` and start the server once so the default config is written.
   Stop the server.
2. Create a plot world. With Multiverse-Core:
   ```
   /mv create plots normal -g PlotMe
   ```
   Without Multiverse, add it to `bukkit.yml`:
   ```yaml
   worlds:
     plots:
       generator: PlotMe
   ```
3. Start the server, teleport in, `/plotme claim`.

Block keys in `worlds.<world>` accept either modern Material names
(`OAK_PLANKS`, `QUARTZ_SLAB`) or the legacy `<id>[:<data>]` form (`5`,
`44:7`) so configs from older PlotMe installs keep working.

Key config flags (`plugins/PlotMe/config.yml`)
----------------------------------------------
- `use-legacy-texts: false` — when `true`, strips color codes and uses a plain
  `[PlotMe] ` chat prefix; when `false` (default) chat is colorized.
- `merge-enabled: true` — master switch for `/plotme merge`.
- `merge-max: 4` — max plots per cluster. Tiered permissions:
  `plotme.merge.limit.4 / 6 / 9 / 16 / *`.
- `mergeCost: 100.0` — Vault price per merge when economy is enabled.
- `webmap.bluemap` / `webmap.squaremap` — enable/disable web-map markers
  independently. `marker-color` is the hex RGB fill.

License
-------
GPL-3.0, same as the upstream project.

Credits
-------
Original authors: ZachBora, MattBDev, and the rest of the WorldCretornica
PlotMe team. Upstream repos were archived in 2020 and this fork picks up
where they stopped.

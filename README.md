PlotMe-Core
===========

Plot management plugin for Paper 1.21+. This is a maintenance fork of the
abandoned WorldCretornica/PlotMe-Core, modernized for current Minecraft and
merged with the (also abandoned) PlotMe-DefaultGenerator so the whole plot
system ships as a single jar.

Status: **v1.0.0** — compiles and runs on Paper 1.21.4 / Java 21.

What's in the jar
-----------------
- All original `/plotme` commands and permissions, behavior preserved.
- Built-in plot-world generator (no separate companion plugin needed).
- bStats metrics, shaded under `com.worldcretornica.plotme_core.libs.bstats`.

Optional integrations (soft-deps)
---------------------------------
- **Vault** — economy hooks for claim/sell/biome/etc. pricing.
- **WorldEdit 7** — WE-anywhere permission + per-plot edit restriction.

Setup
-----
1. Drop `PlotMe-Core.jar` into `plugins/` and start the server once so the
   default config is written. Stop the server.
2. Create a plot world. With Multiverse-Core:
   ```
   /mv create plotworld normal -g PlotMe
   ```
   Without Multiverse, add it to `bukkit.yml`:
   ```yaml
   worlds:
     plotworld:
       generator: PlotMe
   ```
3. Start the server, teleport in, `/plotme claim`.

Block keys in `worlds.<world>` accept either modern Material names
(`OAK_PLANKS`, `QUARTZ_SLAB`) or the legacy `<id>[:<data>]` form (`5`,
`44:7`) so configs from older PlotMe installs keep working.

Building
--------
```
mvn clean package
```
Java 21 + Maven 3.6+. Output: `target/PlotMe-Core.jar` (uber-jar, bStats
shaded).

License
-------
GPL-3.0, same as the upstream project.

Credits
-------
Original authors: ZachBora, MattBDev, and the rest of the WorldCretornica
PlotMe team. Upstream repos were archived in 2020 and this fork picks up
where they stopped.

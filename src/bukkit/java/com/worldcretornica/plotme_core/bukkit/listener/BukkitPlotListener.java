package com.worldcretornica.plotme_core.bukkit.listener;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.IEntity;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.Location;
import com.worldcretornica.plotme_core.api.event.PlotWorldLoadEvent;
import com.worldcretornica.plotme_core.api.event.eventbus.Subscribe;
import com.worldcretornica.plotme_core.bukkit.BukkitUtil;
import com.worldcretornica.plotme_core.bukkit.PlotMe_CorePlugin;
import com.worldcretornica.plotme_core.bukkit.api.BukkitEntity;
import com.worldcretornica.plotme_core.bukkit.api.BukkitPlayer;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import com.worldcretornica.plotme_core.flag.StandardFlags;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class BukkitPlotListener implements Listener {

    private final PlotMe_Core api;
    private final PlotMe_CorePlugin plugin;
    private final PlotMeCoreManager manager;

    public BukkitPlotListener(PlotMe_CorePlugin plotMeCorePlugin) {
        api = plotMeCorePlugin.getAPI();
        this.plugin = plotMeCorePlugin;
        manager = PlotMeCoreManager.getInstance();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        org.bukkit.Location bloc = event.getBlock().getLocation();
        Location location = BukkitUtil.adapt(bloc);

        if (manager.isPlotWorld(location)) {
            if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                return;
            }
            // getPlotOrMergedRoad: a block on a merge-filled road strip
            // resolves to the cluster's plot, so the cluster owner can build
            // on the now-visually-attached road. Unclaimed/non-merged road
            // still returns null → CannotBuild, preserving original behaviour.
            Plot plot = manager.getPlotOrMergedRoad(location);
            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                if (plot.getOwnerId().equals(player.getUniqueId())) {
                    return;
                }
                Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                        return;
                    } else if (api.isPlotLocked(plot.getId())) {
                        player.sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                    if (plot.getExpiredDate() != null) {
                        // Day-granularity is sufficient for expiry resets — skip the write
                        // if today's reset would land on the same day already stored.
                        LocalDate expired = plot.getExpiredDate();
                        LocalDate target = LocalDate.now().plusDays(manager.getMap(player).getDaysToExpiration());
                        if (!expired.equals(target)) {
                            plot.resetExpire(manager.getMap(player).getDaysToExpiration());
                            plugin.getAPI().getSqlManager().savePlot(plot);
                        }
                    }
                } else {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        Location location = BukkitUtil.adapt(event.getBlockPlaced().getLocation());

        if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
            return;
        }
        if (manager.isPlotWorld(location)) {
            // See onBlockBreak: merge-filled road strips resolve to the
            // cluster's plot so the owner can keep building on them.
            Plot plot = manager.getPlotOrMergedRoad(location);
            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                if (plot.getOwnerId().equals(player.getUniqueId())) {
                    return;
                }
                Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                        return;
                    } else if (api.isPlotLocked(plot.getId())) {
                        player.sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                    if (plot.getExpiredDate() != null) {
                        // Day-granularity is sufficient for expiry resets — skip the write
                        // if today's reset would land on the same day already stored.
                        LocalDate expired = plot.getExpiredDate();
                        LocalDate target = LocalDate.now().plusDays(manager.getMap(player).getDaysToExpiration());
                        if (!expired.equals(target)) {
                            plot.resetExpire(manager.getMap(player).getDaysToExpiration());
                            plugin.getAPI().getSqlManager().savePlot(plot);
                        }
                    }
                } else {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmptyEvent(PlayerBucketEmptyEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        Location location = BukkitUtil.adapt(event.getBlockClicked().getLocation());

        if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
            return;
        }
        if (manager.isPlotWorld(location)) {
            Plot plot = manager.getPlot(location.add(event.getBlockFace().getModX(), event.getBlockFace().getModY(), event.getBlockFace().getModZ()));
            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    } else if (api.isPlotLocked(plot.getId())) {
                        player.sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFillEvent(PlayerBucketFillEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        Location location = BukkitUtil.adapt(event.getBlockClicked().getLocation());

        if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
            return;
        }
        if (manager.isPlotWorld(location)) {
            Plot plot = manager.getPlot(location);

            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    } else if (api.isPlotLocked(plot.getId())) {
                        player.sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        Location location = BukkitUtil.adapt(event.getClickedBlock().getLocation());
        PlotMapInfo pmi = manager.getMap(location);
        if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
            return;
        }
        if (manager.isPlotWorld(location)) {
            Plot plot = manager.getPlot(location);
            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else if (!plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED)) {
                        if (!api.getServerBridge().getOfflinePlayer(plot.getOwnerId())
                                .isOnline()) {
                            if (event.hasBlock() && pmi.isProtectedBlock(event.getClickedBlock().getType())) {
                                Material clicked = event.getClickedBlock().getType();
                                if (!player.hasPermission("plotme.unblock." + clicked.name())) {
                                    player.sendMessage(api.C("CannotBuild"));
                                    event.setCancelled(true);
                                    return;
                                } else {
                                    return;
                                }
                            }
                            if (event.hasItem() && pmi.isPreventedItem(event.getItem().getType())) {
                                Material clicked = event.getClickedBlock().getType();
                                if (!player.hasPermission("plotme.unblock." + clicked.name())) {
                                    player.sendMessage(api.C("CannotBuild"));
                                    event.setCancelled(true);
                                }

                            }
                        }
                    }
                } else {
                    if (event.hasBlock() && pmi.isProtectedBlock(event.getClickedBlock().getType())) {
                        Material clicked = event.getClickedBlock().getType();
                        if (player.hasPermission("plotme.unblock." + clicked.name())) {
                            return;
                        } else {
                            player.sendMessage(api.C("CannotBuild"));
                            event.setCancelled(true);
                            return;
                        }
                    }
                    if (event.hasItem() && pmi.isPreventedItem(event.getItem().getType())) {
                        Material clicked = event.getClickedBlock().getType();
                        if (!player.hasPermission("plotme.unblock." + clicked.name())) {
                            player.sendMessage(api.C("CannotBuild"));
                            event.setCancelled(true);
                        }

                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location)) {
            PlotId id = manager.getPlotId(location);

            if (id == null) {
                event.setCancelled(true);
                return;
            }
            if (api.isPlotLocked(id)) {
                event.setCancelled(true);
                return;
            }
            // Fire spread is its own per-plot toggle. We treat any spread
            // of FIRE blocks as "fire spread" for flag purposes; cancelling
            // here stops fire from creeping across the plot.
            if (event.getNewState() != null && event.getNewState().getType() == Material.FIRE) {
                Plot plot = manager.getPlot(location);
                if (plot != null && !plot.getFlagValue(StandardFlags.FIRE_SPREAD)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location)) {
            PlotId id = manager.getPlotId(location);

            if (id == null) {
                event.setCancelled(true);
            } else {
                event.setCancelled(api.isPlotLocked(id));

            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location)) {
            PlotId id = manager.getPlotId(location);

            if (id == null) {
                event.setCancelled(true);
            } else {
                event.setCancelled(api.isPlotLocked(id));

            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location)) {
            PlotId id = manager.getPlotId(location);

            if (id == null) {
                event.setCancelled(true);
            } else {
                event.setCancelled(api.isPlotLocked(id));

            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());
        if (manager.isPlotWorld(location)) {
            PlotId id = manager.getPlotId(location);
            if (id == null) {
                event.setCancelled(true);
            } else {
                event.setCancelled(api.isPlotLocked(id));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location)) {
            PlotId id = manager.getPlotId(location);

            if (id == null) {
                event.setCancelled(true);
            } else {
                event.setCancelled(api.isPlotLocked(id));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        BukkitWorld world = BukkitUtil.adapt(event.getBlock().getWorld());
        if (manager.isPlotWorld(world)) {
            BlockFace face = event.getDirection();

            for (Block block : event.getBlocks()) {
                PlotId id = manager.getPlotId(new Location(world, BukkitUtil.locationToVector(
                        block.getLocation().add(face.getModX(), face.getModY(),
                                face.getModZ()))));
                if (id == null) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        BukkitWorld world = BukkitUtil.adapt(event.getBlock().getWorld());
        if (manager.isPlotWorld(world)) {
            List<Block> blocks = event.getBlocks();
            for (Block moved : blocks) {
                PlotId id = manager.getPlotId(new Location(world, BukkitUtil.locationToVector(moved.getLocation())));
                if (id == null) {
                    event.setCancelled(true);
                } else {
                    event.setCancelled(api.isPlotLocked(id));

                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        BukkitWorld world = new BukkitWorld(event.getWorld());
        if (manager.isPlotWorld(world)) {
            for (int i = 0; i < event.getBlocks().size(); i++) {
                PlotId id = manager.getPlotId(BukkitUtil.adapt(event.getBlocks().get(i).getLocation()));
                if (id == null) {
                    event.getBlocks().remove(i);
                    i--;
                } else {
                    event.setCancelled(api.isPlotLocked(id));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        IEntity entity = plugin.wrapEntity(event.getEntity());
        if (manager.isPlotWorld(entity)) {
            PlotMapInfo pmi = manager.getMap(entity.getLocation());

            if (pmi != null && pmi.isDisableExplosion()) {
                event.setCancelled(true);
            } else {
                Plot plot = manager.getPlot(entity.getLocation());
                if (plot == null) {
                    event.setCancelled(true);
                } else if (!plot.getFlagValue(StandardFlags.EXPLOSION)) {
                    // Per-plot opt-out via flag.
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());
        PlotMapInfo pmi = manager.getMap(location);

        if (pmi != null && pmi.isDisableExplosion()) {
            event.setCancelled(true);
        } else {
            Plot plot = manager.getPlot(location);
            if (plot == null) {
                event.setCancelled(true);
            } else if (!plot.getFlagValue(StandardFlags.EXPLOSION)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getIgnitingEntity() == null) {
            return;
        }
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        PlotMapInfo pmi = manager.getMap(location);

        if (pmi != null) {
            if (pmi.isDisableIgnition()) {
                event.setCancelled(true);
            } else {
                Plot plot = manager.getPlot(location);
                if (plot == null) {
                    event.setCancelled(true);
                } else {
                    if (event.getPlayer() != null && plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                        return;
                    }
                    Optional<Plot.AccessLevel> member = plot.isMember(event.getIgnitingEntity().getUniqueId());
                    if (member.isPresent()) {
                        if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                            event.setCancelled(true);
                        } else if (api.isPlotLocked(plot.getId())) {
                            event.setCancelled(true);
                        }
                    } else {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location.getWorld())) {
            if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                return;
            }

            Plot plot = manager.getPlot(location);

            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    } else if (api.isPlotLocked(plot.getId())) {
                        player.sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {

        if (event.getRemover() instanceof Player) {
            BukkitPlayer player = (BukkitPlayer) plugin.wrapPlayer((Player) event.getRemover());

            if (manager.isPlotWorld(player)) {
                if (player.hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                    return;
                }

                Plot plot = manager.getPlot(player);

                if (plot == null) {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                } else {
                    if (plot.getOwnerId().equals(player.getUniqueId())) {
                        return;
                    }
                    Optional<Plot.AccessLevel> member = plot.isMember(player.getUniqueId());
                    if (member.isPresent()) {
                        if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                            player.sendMessage(api.C("CannotBuild"));
                            event.setCancelled(true);
                        } else if (api.isPlotLocked(plot.getId())) {
                            player.sendMessage(api.C("PlotLocked"));
                            event.setCancelled(true);
                        }
                    } else {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Location location = BukkitUtil.adapt(event.getRightClicked().getLocation());
        if (manager.isPlotWorld(location)) {
            //Citizens Support
            if (event.getRightClicked().hasMetadata("NPC") && event.getRightClicked().getMetadata("NPC").get(0).asBoolean()) {
                return;
            }

            Plot plot = manager.getPlot(location);

            if (plot == null) {
                if (!event.getPlayer().hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                    event.getPlayer().sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            } else {
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                if (!event.getPlayer().hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                    Optional<Plot.AccessLevel> member = plot.isMember(event.getPlayer().getUniqueId());
                    if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                        return;
                    }
                    if (member.isPresent()) {
                        if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                            event.getPlayer().sendMessage(api.C("CannotBuild"));
                            event.setCancelled(true);
                        } else if (api.isPlotLocked(plot.getId())) {
                            event.getPlayer().sendMessage(api.C("PlotLocked"));
                            event.setCancelled(true);
                        }
                    } else {
                        event.getPlayer().sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunchEvent(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Player) {
            PlotMapInfo pmi = manager.getMap(BukkitUtil.adapt(event.getEntity().getWorld()));
            if (pmi != null && !pmi.canUseProjectiles()) {
                event.getEntity().sendMessage(api.C("ErrCannotUseEggs"));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location location = BukkitUtil.adapt(event.getLocation());

        if (!manager.isPlotWorld(location)) {
            return;
        }

        // Spawns on the road (not inside any plot) keep the old behaviour:
        // unconditionally cancelled. Roads aren't owned by anyone so we
        // can't consult flags there.
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            event.setCancelled(true);
            return;
        }

        // Spawner / spawn-egg / breeding / etc. -- treat as player intent
        // and let it through regardless of flags. We only police the
        // *natural* spawn categories that floods the plot with mobs.
        switch (event.getSpawnReason()) {
            case SPAWNER:
            case SPAWNER_EGG:
            case BREEDING:
            case EGG:
            case CUSTOM:
            case BUILD_IRONGOLEM:
            case BUILD_SNOWMAN:
            case BUILD_WITHER:
            case DISPENSE_EGG:
            case CURED:
            case SLIME_SPLIT:
            case TRAP:
            case SHOULDER_ENTITY:
                return;
            default:
                break;
        }

        // Master switch first; if mob-spawning is off, nothing natural spawns.
        if (!plot.getFlagValue(StandardFlags.MOB_SPAWNING)) {
            event.setCancelled(true);
            return;
        }

        // Then the more specific monster/animal toggles.
        if (event.getEntity() instanceof Monster) {
            if (!plot.getFlagValue(StandardFlags.MONSTER_SPAWNING)) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Animals) {
            if (!plot.getFlagValue(StandardFlags.ANIMAL_SPAWNING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Location loc = BukkitUtil.adapt(event.getEntity().getLocation());
        if (manager.isPlotWorld(loc)) {
            // Damage to monsters is never policed by plot rules -- you can
            // always kill the zombie that wandered in.
            if (event.getEntity() instanceof Monster) {
                return;
            }
            //This includes everything except for Monsters which were excluded above.
            if (event.getCause().equals(EntityDamageEvent.DamageCause.ENTITY_ATTACK) && event instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;
                //Specific to Players to allow PVP. event.getEntity() is the damaged entity
                if (event.getEntity() instanceof Player) {
                    Plot plot = manager.getPlot(loc);
                    Plot plot2 = manager.getPlot(BukkitUtil.adapt(damageByEntityEvent.getDamager().getLocation()));

                    // Hostile mob hits a player -> hostile-mob-attack flag.
                    if (damageByEntityEvent.getDamager() instanceof Monster) {
                        if (plot != null && !plot.getFlagValue(StandardFlags.HOSTILE_MOB_ATTACK)) {
                            event.setCancelled(true);
                        }
                        return;
                    }

                    // Player-vs-player. Honour the legacy "no PVP on roads"
                    // rule, but additionally require the pvp flag on the
                    // victim's plot.
                    if (plot == null || plot2 == null) {
                        event.setCancelled(true);
                        return;
                    }
                    if (!plot.getFlagValue(StandardFlags.PVP)) {
                        event.setCancelled(true);
                    }
                } else {
                    Plot plot = manager.getPlot(loc);
                    if (plot == null) {
                        event.setCancelled(true);
                    } else {
                        if (plot.getOwnerId().equals(((EntityDamageByEntityEvent) event).getDamager().getUniqueId())) {
                            return;
                        }
                        Optional<Plot.AccessLevel> member = plot.isMember(((EntityDamageByEntityEvent) event).getDamager().getUniqueId());
                        if (member.isPresent() && member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge()
                                .getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                            event.setCancelled(true);
                        } else {
                            return;
                        }
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Per-plot flag handlers below. These are deliberately small and
    // self-contained so individual flags can be disabled/edited without
    // touching the rest of the listener.
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());
        if (!manager.isPlotWorld(location)) {
            return;
        }
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            // Outside any plot (road) -- preserve the same "no burning" road
            // semantics block-spread already enforces.
            event.setCancelled(true);
            return;
        }
        if (!plot.getFlagValue(StandardFlags.BLOCK_BURN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());
        if (!manager.isPlotWorld(location)) {
            return;
        }
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            return; // road leaves can still decay
        }
        if (!plot.getFlagValue(StandardFlags.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Location location = BukkitUtil.adapt(event.getItemDrop().getLocation());
        if (!manager.isPlotWorld(location)) {
            return;
        }
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            return;
        }
        // Owner is exempt; their own plot rules don't lock them out.
        if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!plot.getFlagValue(StandardFlags.ITEM_DROP)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(api.C("CannotBuild"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Location location = BukkitUtil.adapt(event.getItem().getLocation());
        if (!manager.isPlotWorld(location)) {
            return;
        }
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            return;
        }
        if (plot.getOwnerId().equals(player.getUniqueId())) {
            return;
        }
        if (!plot.getFlagValue(StandardFlags.ITEM_PICKUP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        // We only police the moment a player tries to *enter* flight.
        // Letting them land is fine.
        if (!event.isFlying()) {
            return;
        }
        Player player = event.getPlayer();
        // Creative / spectator can always fly -- the flag is about giving
        // survival players flight on plots that allow it.
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location location = BukkitUtil.adapt(player.getLocation());
        if (!manager.isPlotWorld(location)) {
            return;
        }
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            return;
        }
        if (!plot.getFlagValue(StandardFlags.FLY)) {
            event.setCancelled(true);
        }
    }

    /**
     * Apply per-plot time / weather overrides as the player crosses plot
     * boundaries. We compare the plot under the player's previous block
     * coords to the plot under their new block coords to avoid running on
     * every sub-block movement.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Cheap fast-path: ignore sub-block movements -- the plot under us
        // can't have changed.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!manager.isPlotWorld(BukkitUtil.adapt(to))) {
            // Walking out of a plot world; reset overrides if we previously
            // applied any.
            resetPlayerOverrides(event.getPlayer());
            return;
        }
        Plot toPlot = manager.getPlot(BukkitUtil.adapt(to));
        applyTimeAndWeather(event.getPlayer(), toPlot);
    }

    private void applyTimeAndWeather(Player player, Plot plot) {
        if (plot == null) {
            resetPlayerOverrides(player);
            return;
        }
        int time = plot.getFlagValue(StandardFlags.TIME);
        if (time < 0) {
            player.resetPlayerTime();
        } else {
            // setPlayerTime(time, false) means "lock the client at this
            // absolute tick of day" rather than offsetting from server time.
            player.setPlayerTime(time, false);
        }
        String weather = plot.getFlagValue(StandardFlags.WEATHER);
        switch (weather) {
            case "clear":
                player.setPlayerWeather(WeatherType.CLEAR);
                break;
            case "rain":
            case "storm":
                player.setPlayerWeather(WeatherType.DOWNFALL);
                break;
            case "unset":
            default:
                player.resetPlayerWeather();
                break;
        }
    }

    private void resetPlayerOverrides(Player player) {
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    /**
     * Apply the {@code use-deny} flag: even plot members are forbidden from
     * right-clicking listed block types (think doors / chests / buttons the
     * owner wants to keep private). Runs after the main interact handler so
     * we don't second-guess existing CannotBuild messaging.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractUseDeny(PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Location location = BukkitUtil.adapt(event.getClickedBlock().getLocation());
        if (!manager.isPlotWorld(location)) {
            return;
        }
        Plot plot = manager.getPlot(location);
        if (plot == null) {
            return;
        }
        // Plot owners and admins are exempt -- the flag is "deny *members*
        // and visitors", not "deny everyone".
        if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getPlayer().hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
            return;
        }
        java.util.List<String> denied = plot.getFlagValue(StandardFlags.USE_DENY);
        if (denied.isEmpty()) {
            return;
        }
        String materialName = event.getClickedBlock().getType().name();
        for (String entry : denied) {
            if (entry.equalsIgnoreCase(materialName)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(api.C("CannotBuild"));
                return;
            }
        }
    }

    @EventHandler
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        Location location = BukkitUtil.adapt(event.getRightClicked().getLocation());

        if (manager.isPlotWorld(location)) {
            if (event.getPlayer().hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                return;
            }

            Plot plot = manager.getPlot(location);

            if (plot == null) {
                event.getPlayer().sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                Optional<Plot.AccessLevel> member = plot.isMember(event.getPlayer().getUniqueId());
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        event.getPlayer().sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    } else if (api.isPlotLocked(plot.getId())) {
                        event.getPlayer().sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                } else {
                    event.getPlayer().sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSandCannon(EntityChangeBlockEvent event) {
        BukkitEntity entity = new BukkitEntity(event.getEntity());
        if (manager.isPlotWorld(entity) && event.getEntity() instanceof FallingBlock) {
            if (event.getTo().equals(Material.AIR)) {
                entity.setMetadata("plotFallBlock", new FixedMetadataValue(plugin, event.getBlock().getLocation()));
            } else {
                List<MetadataValue> values = entity.getMetadata("plotFallBlock");

                if (!values.isEmpty()) {

                    org.bukkit.Location spawn = (org.bukkit.Location) (values.get(0).value());
                    org.bukkit.Location createdNew = event.getBlock().getLocation();

                    if (spawn.getBlockX() != createdNew.getBlockX() || spawn.getBlockZ() != createdNew.getBlockZ()) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPlotWorldLoad(PlotWorldLoadEvent event) {
        api.getLogger().log(Level.INFO, "Done loading {0} plots for world {1}", new Object[]{event.getNbPlots(), event
                .getWorldName()});
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        plugin.removePlayer(playerUUID);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        if (p != null) {
            manager.UpdatePlayerNameFromId(p.getUniqueId(), p.getName());
            // If they logged in standing on a plot with time/weather flags
            // set, apply those overrides now -- PlayerMoveEvent won't fire
            // until they actually move.
            Location loc = BukkitUtil.adapt(p.getLocation());
            if (manager.isPlotWorld(loc)) {
                applyTimeAndWeather(p, manager.getPlot(loc));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignEdit(SignChangeEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());
        Location location = BukkitUtil.adapt(event.getBlock().getLocation());

        if (manager.isPlotWorld(location)) {
            if (event.getPlayer().hasPermission(PermissionNames.ADMIN_BUILDANYWHERE)) {
                return;
            }
            Plot plot = manager.getPlot(location);
            if (plot == null) {
                player.sendMessage(api.C("CannotBuild"));
                event.setCancelled(true);
            } else {
                Optional<Plot.AccessLevel> member = plot.isMember(event.getPlayer().getUniqueId());
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                if (member.isPresent()) {
                    if (member.get().equals(Plot.AccessLevel.TRUSTED) && !api.getServerBridge().getOfflinePlayer(plot.getOwnerId()).isOnline()) {
                        player.sendMessage(api.C("CannotBuild"));
                        event.setCancelled(true);
                    } else if (api.isPlotLocked(plot.getId())) {
                        player.sendMessage(api.C("PlotLocked"));
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(api.C("CannotBuild"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/reload") || event.getMessage().equalsIgnoreCase("reload")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cPlotMe disabled /reload to prevent errors from occurring.");
        }
    }

    @EventHandler
    public void onCommand(ServerCommandEvent event) {
        if (event.getCommand().equalsIgnoreCase("/reload") || event.getCommand().equalsIgnoreCase("reload")) {
            event.setCommand("");
            event.getSender().sendMessage("§cPlotMe disabled /reload to prevent errors from occurring.");
        }
    }
}

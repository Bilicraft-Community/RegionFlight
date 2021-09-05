package com.bilicraft.regionflight;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class RegionFlight extends JavaPlugin implements Listener {
    private StateFlag regionFlightFlag = null;
    private RegionContainer container;
    private final Set<UUID> affectedPlayers = new HashSet<>();

    @Override
    public void onLoad() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            regionFlightFlag = new StateFlag("region-flight", false);
            registry.register(regionFlightFlag);
        } catch (FlagConflictException e) {
            // some other plugin registered a flag by the same name already.
            // you can use the existing flag, but this may cause conflicts - be sure to check type
            regionFlightFlag = (StateFlag) registry.get("region-flight");
        } catch (IllegalStateException exception) {
            getLogger().log(Level.WARNING, "Failed register flags in this time", exception);
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getOnlinePlayers().forEach(p -> checkAndApply(p, false));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getOnlinePlayers().forEach(p -> checkAndApply(p, false));
    }

    private void checkAndApply(Player player, boolean onlyDisable) {
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        if (set.queryState(localPlayer, regionFlightFlag) == StateFlag.State.ALLOW) {
            if (!onlyDisable) {
                if(player.hasPermission("regionflight.allow")) {
                    player.setAllowFlight(true);
                    affectedPlayers.add(player.getUniqueId());
                }
            }
        } else {
            if (affectedPlayers.remove(player.getUniqueId())) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null) {
            if (equalsBlockStateLocation(event.getFrom(), event.getTo())) return;
        }
        checkAndApply(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        checkAndApply(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        checkAndApply(event.getPlayer(), true);
    }


    private boolean equalsBlockStateLocation(org.bukkit.Location b1, Location b2) {
        if (!Objects.requireNonNull(b1.getWorld()).equals(b2.getWorld())) return false;
        return (b1.getBlockX() == b2.getBlockX())
                && (b1.getBlockY() == b2.getBlockY())
                && (b1.getBlockZ() == b2.getBlockZ());
    }
}

package me.admintools;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeManager implements Listener {
    private final AdminTools pl;
    private final Set<UUID> moveFrozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> invFrozen = ConcurrentHashMap.newKeySet();

    public FreezeManager(AdminTools pl) {
        this.pl = pl;
        pl.db.query("SELECT uuid, type FROM freezes", rs -> {
            UUID u = UUID.fromString(rs.getString(1));
            (rs.getString(2).equals("INV") ? invFrozen : moveFrozen).add(u);
            return null;
        });
    }

    /** type: "MOVE" or "INV" */
    public void set(UUID u, String type, boolean on) {
        Set<UUID> set = type.equals("INV") ? invFrozen : moveFrozen;
        if (on) set.add(u); else set.remove(u);
        pl.async(() -> {
            if (on) pl.db.update("INSERT OR IGNORE INTO freezes(uuid,type) VALUES(?,?)", u.toString(), type);
            else pl.db.update("DELETE FROM freezes WHERE uuid=? AND type=?", u.toString(), type);
        });
    }

    private boolean inv(Entity e) { return e instanceof Player && invFrozen.contains(e.getUniqueId()); }

    // ---------- movement freeze ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (!moveFrozen.contains(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            t.setX(f.getX()); t.setY(f.getY()); t.setZ(f.getZ());
            e.setTo(t); // still allows looking around
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (!moveFrozen.contains(e.getPlayer().getUniqueId())) return;
        var cause = e.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        if (moveFrozen.contains(u)) Util.msg(e.getPlayer(), "&cYou are frozen. Do not leave the server!");
        if (invFrozen.contains(u)) Util.msg(e.getPlayer(), "&cYour inventory is frozen.");
    }

    // ---------- inventory freeze (inventory + ender chest + hands) ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) { if (inv(e.getWhoClicked())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) { if (inv(e.getWhoClicked())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) { if (inv(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent e) { if (inv(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) { if (inv(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) { if (inv(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent e) { if (inv(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && invFrozen.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if (inv(e.getPlayer()) && e.hasItem()) e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (invFrozen.contains(e.getPlayer().getUniqueId())) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
    }
}

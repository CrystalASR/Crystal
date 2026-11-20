package me.admintools;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * /invsee /endersee (read-only)  and  /sinvsee /sendersee (editable).
 *
 * Inventory layout (54 slots):
 *   0-26  = main inventory (rows)      27-35 = hotbar
 *   36-39 = helmet, chestplate, leggings, boots     40 = off-hand
 *   everything else = filler glass
 */
public final class ViewManager implements Listener {
    public enum Kind { INV, END }

    public static final class Holder implements InventoryHolder {
        final Kind kind;
        final UUID target;
        final boolean editable;
        Inventory inv;

        Holder(Kind kind, UUID target, boolean editable) {
            this.kind = kind; this.target = target; this.editable = editable;
        }

        @Override public Inventory getInventory() { return inv; }
    }

    private static final ItemStack FILLER = makeFiller();

    private static ItemStack makeFiller() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        m.setHideTooltip(true);
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }

    private final AdminTools pl;

    public ViewManager(AdminTools pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::refresh, 10L, 10L);
    }

    /** GUI slot -> real slot in the target's inventory / ender chest, or -1 for filler. */
    static int slot(Kind k, int g) {
        if (k == Kind.END) return g < 27 ? g : -1;
        if (g < 27) return g + 9;
        if (g < 36) return g - 27;
        if (g < 40) return 75 - g;   // 36->39 helmet ... 39->36 boots
        if (g == 40) return 40;      // off-hand
        return -1;
    }

    private static Holder holder(Inventory inv) {
        return inv.getHolder(false) instanceof Holder h ? h : null;
    }

    public void open(Player viewer, Player target, Kind k, boolean edit) {
        Holder h = new Holder(k, target.getUniqueId(), edit);
        String title = (edit ? "&c" : "&7") + (k == Kind.INV ? "Inventory: " : "EnderChest: ")
                + target.getName() + (edit ? "" : " (view only)");
        h.inv = Bukkit.createInventory(h, k == Kind.INV ? 54 : 27, Util.c(title));
        fill(h, target);
        viewer.openInventory(h.inv);
    }

    private void fill(Holder h, Player t) {
        for (int i = 0; i < h.inv.getSize(); i++) {
            int s = slot(h.kind, i);
            if (s < 0) h.inv.setItem(i, FILLER);
            else h.inv.setItem(i, h.kind == Kind.INV ? t.getInventory().getItem(s) : t.getEnderChest().getItem(s));
        }
    }

    private void apply(Holder h, Player t) {
        for (int i = 0; i < h.inv.getSize(); i++) {
            int s = slot(h.kind, i);
            if (s < 0) continue;
            ItemStack it = h.inv.getItem(i);
            if (h.kind == Kind.INV) t.getInventory().setItem(s, it);
            else t.getEnderChest().setItem(s, it);
        }
    }

    private void applyLater(Holder h) {
        Bukkit.getScheduler().runTask(pl, () -> {
            Player t = Bukkit.getPlayer(h.target);
            if (t != null) apply(h, t);
        });
    }

    private void refresh() {
        for (Player v : Bukkit.getOnlinePlayers()) {
            Holder h = holder(v.getOpenInventory().getTopInventory());
            if (h == null) continue;
            Player t = Bukkit.getPlayer(h.target);
            if (t == null) { v.closeInventory(); continue; }
            if (h.editable && !v.getItemOnCursor().getType().isAir()) continue; // don't disturb a drag/move
            fill(h, t);
        }
    }

    // ---------- listeners ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        Holder h = holder(top);
        if (h == null) return;
        Player t = Bukkit.getPlayer(h.target);
        if (!h.editable || t == null) { e.setCancelled(true); return; }

        if (top.equals(e.getClickedInventory()) && slot(h.kind, e.getRawSlot()) < 0) { e.setCancelled(true); return; }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        applyLater(h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        Holder h = holder(top);
        if (h == null) return;
        if (!h.editable || Bukkit.getPlayer(h.target) == null) { e.setCancelled(true); return; }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize() && slot(h.kind, raw) < 0) { e.setCancelled(true); return; }
        }
        applyLater(h);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Holder h = holder(e.getInventory());
        if (h == null || !h.editable) return;
        Player t = Bukkit.getPlayer(h.target);
        if (t != null) apply(h, t);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID quitting = e.getPlayer().getUniqueId();
        for (Player v : Bukkit.getOnlinePlayers()) {
            Holder h = holder(v.getOpenInventory().getTopInventory());
            if (h != null && h.target.equals(quitting)) v.closeInventory();
        }
    }
}

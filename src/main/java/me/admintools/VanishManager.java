package me.admintools;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishManager implements Listener {
    /** Broadcast a vanilla "left/joined the game" message when toggling vanish. */
    private static final boolean FAKE_MESSAGES = true;
    private static final String SEE = "admintools.vanish.see";

    private final AdminTools pl;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<String> names = ConcurrentHashMap.newKeySet(); // lowercase, for async tab filtering

    public VanishManager(AdminTools pl) {
        this.pl = pl;
        // Action bar indicator for vanished staff
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (UUID u : vanished) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) p.sendActionBar(Util.c("&a&lVANISH: &2ON"));
            }
        }, 20L, 20L);
    }

    public boolean isVanished(UUID u) { return vanished.contains(u); }

    public boolean canSee(CommandSender s) { return s.hasPermission(SEE); }

    public void set(Player p, boolean on) { apply(p, on, true); }

    private void apply(Player p, boolean on, boolean announce) {
        if (on) { vanished.add(p.getUniqueId()); names.add(p.getName().toLowerCase()); }
        else { vanished.remove(p.getUniqueId()); names.remove(p.getName().toLowerCase()); }

        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            if (on && !canSee(o)) o.hidePlayer(pl, p);   // removes entity + tab entry for that client
            else o.showPlayer(pl, p);
        }
        p.setCollidable(!on);
        p.setSleepingIgnored(on);
        p.setAffectsSpawning(!on);

        if (!announce) return;
        if (on) {
            Util.msg(p, "&aVanish enabled. Nobody can see you.");
        } else {
            Util.msg(p, "&cVanish disabled. You are visible.");
            p.sendActionBar(Util.c("&c&lVANISH: &4OFF"));
        }
        if (FAKE_MESSAGES) {
            Bukkit.broadcast(Component.translatable(on ? "multiplayer.player.left" : "multiplayer.player.joined",
                    NamedTextColor.YELLOW, p.name()));
        }
    }

    // ---------- listeners ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player j = e.getPlayer();
        for (UUID u : vanished) {
            Player v = Bukkit.getPlayer(u);
            if (v != null && !canSee(j)) j.hidePlayer(pl, v);
        }
        if (j.hasPermission("admintools.vanish.onjoin")) {
            e.joinMessage(null);
            apply(j, true, false);
            Util.msg(j, "&aYou joined vanished.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (vanished.remove(e.getPlayer().getUniqueId())) {
            names.remove(e.getPlayer().getName().toLowerCase());
            e.quitMessage(null);
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        int n = (int) vanished.stream().filter(u -> Bukkit.getPlayer(u) != null).count();
        if (n == 0) return;
        e.setNumPlayers(Math.max(0, e.getNumPlayers() - n));
        e.getListedPlayers().removeIf(i -> vanished.contains(i.id()));
    }

    @EventHandler
    public void onTab(AsyncTabCompleteEvent e) {
        if (names.isEmpty() || canSee(e.getSender())) return;
        var list = e.completions();
        if (list.stream().anyMatch(c -> names.contains(c.suggestion().toLowerCase()))) {
            e.completions(list.stream().filter(c -> !names.contains(c.suggestion().toLowerCase())).toList());
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && vanished.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && vanished.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) e.deathMessage(null);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) e.message(null);
    }
}

package me.admintools;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CpsManager implements Listener {
    private final Map<UUID, ArrayDeque<Long>> clicks = new HashMap<>();
    private final Map<UUID, Integer> peak = new HashMap<>();

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        UUID u = e.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = clicks.computeIfAbsent(u, k -> new ArrayDeque<>());
        q.addLast(now);
        prune(q, now);
        peak.merge(u, q.size(), Math::max);
    }

    private static void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
    }

    public int cps(UUID u) {
        ArrayDeque<Long> q = clicks.get(u);
        if (q == null) return 0;
        prune(q, System.currentTimeMillis());
        return q.size();
    }

    public int peak(UUID u) { return peak.getOrDefault(u, 0); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clicks.remove(e.getPlayer().getUniqueId());
        peak.remove(e.getPlayer().getUniqueId());
    }
}

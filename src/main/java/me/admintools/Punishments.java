package me.admintools;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Punishments implements Listener {

    public record Entry(int id, UUID uuid, String name, String type, String reason,
                        String staff, long created, long expires, boolean active) {
        public boolean isActive() { return active && (expires == 0 || expires > System.currentTimeMillis()); }
        public String remaining() { return expires == 0 ? "never" : Util.fmtDuration(expires - System.currentTimeMillis()); }
    }

    private static final Set<String> CHAT_COMMANDS = Set.of(
            "msg", "tell", "w", "whisper", "r", "reply", "me", "m", "t", "pm", "dm", "teammsg", "tm", "mail");

    private final AdminTools pl;
    private final Map<UUID, Entry> mutes = new ConcurrentHashMap<>();

    public Punishments(AdminTools pl) {
        this.pl = pl;
        for (Entry e : pl.db.query(
                "SELECT * FROM punishments WHERE type='MUTE' AND active=1 AND (expires=0 OR expires>?)",
                Punishments::map, System.currentTimeMillis())) {
            mutes.put(e.uuid(), e);
        }
    }

    private static Entry map(ResultSet rs) throws SQLException {
        return new Entry(rs.getInt("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                rs.getString("type"), rs.getString("reason"), rs.getString("staff"),
                rs.getLong("created"), rs.getLong("expires"), rs.getInt("active") == 1);
    }

    /** durMs: 0 = permanent. Call from the IO thread. */
    public Entry add(UUID uuid, String name, String type, String reason, String staff, long durMs) {
        long now = System.currentTimeMillis();
        long exp = durMs == 0 ? 0 : now + durMs;
        pl.db.update("INSERT INTO punishments(uuid,name,type,reason,staff,created,expires,active) VALUES(?,?,?,?,?,?,?,1)",
                uuid.toString(), name, type, reason, staff, now, exp);
        Entry e = new Entry(0, uuid, name, type, reason, staff, now, exp, true);
        if (type.equals("MUTE")) mutes.put(uuid, e);
        return e;
    }

    public void deactivate(UUID uuid, String type) {
        pl.db.update("UPDATE punishments SET active=0 WHERE uuid=? AND type=? AND active=1", uuid.toString(), type);
        if (type.equals("MUTE")) mutes.remove(uuid);
    }

    public List<Entry> active(UUID uuid, String type) {
        return pl.db.query("SELECT * FROM punishments WHERE uuid=? AND type=? AND active=1 AND (expires=0 OR expires>?) ORDER BY id DESC",
                Punishments::map, uuid.toString(), type, System.currentTimeMillis());
    }

    public List<Entry> history(UUID uuid) {
        return pl.db.query("SELECT * FROM punishments WHERE uuid=? ORDER BY id DESC", Punishments::map, uuid.toString());
    }

    public Entry activeMute(UUID uuid) {
        Entry e = mutes.get(uuid);
        if (e == null) return null;
        if (!e.isActive()) { mutes.remove(uuid); return null; }
        return e;
    }

    // ---------- listeners ----------

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent e) {
        List<Entry> bans = active(e.getUniqueId(), "BAN");
        if (bans.isEmpty()) return;
        Entry b = bans.get(0);
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Util.c(
                "&c&lYou are banned from this server\n\n&7Reason: &f" + b.reason()
                        + "\n&7Expires in: &f" + b.remaining() + "\n&7By: &f" + b.staff()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Entry m = activeMute(e.getPlayer().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
        Util.msg(e.getPlayer(), "&cYou are muted. &7Reason: &f" + m.reason() + " &7| Expires in: &f" + m.remaining());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Entry m = activeMute(e.getPlayer().getUniqueId());
        if (m == null) return;
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        label = label.substring(label.lastIndexOf(':') + 1);
        if (CHAT_COMMANDS.contains(label)) {
            e.setCancelled(true);
            Util.msg(e.getPlayer(), "&cYou are muted. &7Expires in: &f" + m.remaining());
        }
    }
}

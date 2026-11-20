package me.admintools;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

public final class LogManager implements Listener {
    private record Row(long time, String name, String action, String world, int x, int y, int z, String mat) {}

    private final AdminTools pl;

    public LogManager(AdminTools pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { record(e.getPlayer(), "PLACE", e.getBlockPlaced()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { record(e.getPlayer(), "BREAK", e.getBlock()); }

    private void record(Player p, String action, Block b) {
        long t = System.currentTimeMillis();
        String uuid = p.getUniqueId().toString(), name = p.getName();
        String world = b.getWorld().getName(), mat = b.getType().name();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        pl.async(() -> pl.db.update(
                "INSERT INTO blocklog(time,uuid,name,action,world,x,y,z,material) VALUES(?,?,?,?,?,?,?,?,?)",
                t, uuid, name, action, world, x, y, z, mat));
    }

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("time"), rs.getString("name"), rs.getString("action"), rs.getString("world"),
                rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getString("material"));
    }

    public void showNear(CommandSender s, Location l, int r) {
        String w = l.getWorld().getName();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        pl.async(() -> send(s, "Block log within " + r + " blocks", pl.db.query(
                "SELECT * FROM blocklog WHERE world=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? ORDER BY id DESC LIMIT 20",
                LogManager::map, w, x - r, x + r, y - r, y + r, z - r, z + r)));
    }

    public void showPlayer(CommandSender s, String name) {
        pl.async(() -> send(s, "Block log of " + name, pl.db.query(
                "SELECT * FROM blocklog WHERE lower(name)=lower(?) ORDER BY id DESC LIMIT 20", LogManager::map, name)));
    }

    private void send(CommandSender s, String title, List<Row> rows) {
        if (rows.isEmpty()) { Util.msg(s, "&cNo log entries found."); return; }
        s.sendMessage(Util.c("&6&l" + title + " &7(latest " + rows.size() + ")"));
        for (Row r : rows) {
            boolean place = r.action().equals("PLACE");
            s.sendMessage(Util.c("&8" + Util.fmtDate(r.time()) + " &f" + r.name() + " "
                    + (place ? "&aplaced " : "&cbroke ") + "&e" + r.mat()
                    + " &8@ " + r.world() + " " + r.x() + " " + r.y() + " " + r.z()));
        }
    }
}

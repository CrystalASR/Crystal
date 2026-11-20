package me.admintools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Commands implements TabExecutor {
    private static final long WARN_MS = 7L * 24 * 60 * 60 * 1000;
    private final AdminTools pl;

    public Commands(AdminTools pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        return switch (cmd.getName().toLowerCase()) {
            case "invsee" -> view(s, a, ViewManager.Kind.INV, false);
            case "endersee" -> view(s, a, ViewManager.Kind.END, false);
            case "sinvsee" -> view(s, a, ViewManager.Kind.INV, true);
            case "sendersee" -> view(s, a, ViewManager.Kind.END, true);
            case "cps" -> cps(s, a);
            case "itp" -> itp(s, a);
            case "tppos" -> tppos(s, a);
            case "log" -> log(s, a);
            case "vanish" -> vanish(s, a);
            case "invfreeze" -> freeze(s, a, "INV", true);
            case "invunfreeze" -> freeze(s, a, "INV", false);
            case "freeze" -> freeze(s, a, "MOVE", true);
            case "unfreeze" -> freeze(s, a, "MOVE", false);
            case "mute" -> punish(s, a, "MUTE");
            case "ban" -> punish(s, a, "BAN");
            case "warn" -> punish(s, a, "WARN");
            case "unmute" -> lift(s, a, "MUTE");
            case "unban" -> lift(s, a, "BAN");
            case "history" -> history(s, a);
            default -> false;
        };
    }

    /** Online player; vanished players are "not found" for people who can't see them. */
    private Player online(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || (pl.vanish.isVanished(t.getUniqueId()) && !pl.vanish.canSee(s))) {
            Util.msg(s, "&cPlayer not found.");
            return null;
        }
        return t;
    }

    private OfflinePlayer known(CommandSender s, String name) {
        OfflinePlayer t = Util.findPlayer(name);
        if (t == null) Util.msg(s, "&cThat player has never joined this server.");
        return t;
    }

    // ---------- inventory views ----------

    private boolean view(CommandSender s, String[] a, ViewManager.Kind k, boolean edit) {
        if (!(s instanceof Player p)) { Util.msg(s, "&cPlayers only."); return true; }
        if (a.length < 1) return false;
        Player t = online(s, a[0]);
        if (t == null) return true;
        pl.views.open(p, t, k, edit);
        return true;
    }

    // ---------- misc ----------

    private boolean cps(CommandSender s, String[] a) {
        if (a.length < 1) return false;
        Player t = online(s, a[0]);
        if (t == null) return true;
        Util.msg(s, "&e" + t.getName() + " &7CPS: &f" + pl.cps.cps(t.getUniqueId())
                + " &8(peak this session: &f" + pl.cps.peak(t.getUniqueId()) + "&8)");
        return true;
    }

    private boolean itp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Util.msg(s, "&cPlayers only."); return true; }
        if (a.length < 1) return false;
        Player t = online(s, a[0]);
        if (t == null) return true;
        p.teleport(t.getLocation());
        Util.msg(p, "&aTeleported to &f" + t.getName());
        return true;
    }

    private boolean tppos(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Util.msg(s, "&cPlayers only."); return true; }
        if (a.length < 3) return false;
        try {
            Location l = p.getLocation();
            double x = coord(a[0], l.getX()), y = coord(a[1], l.getY()), z = coord(a[2], l.getZ());
            World w = a.length > 3 ? Bukkit.getWorld(a[3]) : l.getWorld();
            if (w == null) { Util.msg(s, "&cUnknown world."); return true; }
            p.teleport(new Location(w, x, y, z, l.getYaw(), l.getPitch()));
            Util.msg(p, "&aTeleported to &f" + x + " " + y + " " + z + " &7(" + w.getName() + ")");
        } catch (NumberFormatException ex) {
            Util.msg(s, "&cInvalid coordinates. Use numbers or ~ for relative.");
        }
        return true;
    }

    private static double coord(String in, double cur) {
        if (in.startsWith("~")) return cur + (in.length() > 1 ? Double.parseDouble(in.substring(1)) : 0);
        return Double.parseDouble(in);
    }

    private boolean log(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) return false;
            pl.logs.showNear(s, p.getLocation(), 5);
        } else if (a[0].matches("\\d+")) {
            if (!(s instanceof Player p)) { Util.msg(s, "&cPlayers only."); return true; }
            pl.logs.showNear(s, p.getLocation(), Math.max(1, Math.min(50, Integer.parseInt(a[0]))));
        } else {
            pl.logs.showPlayer(s, a[0]);
        }
        return true;
    }

    private boolean vanish(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Util.msg(s, "&cPlayers only."); return true; }
        boolean on;
        if (a.length == 0) on = !pl.vanish.isVanished(p.getUniqueId());
        else if (a[0].equalsIgnoreCase("on")) on = true;
        else if (a[0].equalsIgnoreCase("off")) on = false;
        else return false;
        pl.vanish.set(p, on);
        return true;
    }

    // ---------- freeze ----------

    private boolean freeze(CommandSender s, String[] a, String type, boolean on) {
        if (a.length < 1) return false;
        OfflinePlayer t = known(s, a[0]);
        if (t == null) return true;
        pl.freeze.set(t.getUniqueId(), type, on);
        String what = type.equals("INV") ? "inventory" : "movement";
        Util.msg(s, "&f" + a[0] + " &7" + what + " " + (on ? "&cfrozen" : "&aunfrozen"));
        Player o = t.getPlayer();
        if (o != null) {
            Util.msg(o, on ? "&cYour " + what + " has been frozen by staff." : "&aYour " + what + " has been unfrozen.");
        }
        return true;
    }

    // ---------- punishments ----------

    private boolean punish(CommandSender s, String[] a, String type) {
        boolean timed = !type.equals("WARN");
        if (a.length < (timed ? 2 : 2)) return false; // warn needs a reason, mute/ban need a time
        OfflinePlayer t = known(s, a[0]);
        if (t == null) return true;

        long dur = WARN_MS;
        int reasonFrom = 1;
        if (timed) {
            dur = Util.parseDuration(a[1]);
            reasonFrom = 2;
            if (dur < 0) { Util.msg(s, "&cInvalid time. Use 1s, 1m, 1h, 1d or perm."); return true; }
        }
        String reason = a.length > reasonFrom
                ? String.join(" ", Arrays.copyOfRange(a, reasonFrom, a.length)) : "No reason specified";
        final long fdur = dur;
        final String staff = s.getName();
        final String name = t.getName() != null ? t.getName() : a[0];

        pl.async(() -> {
            if (!type.equals("WARN") && !pl.punishments.active(t.getUniqueId(), type).isEmpty()) {
                Util.msg(s, "&c" + name + " is already " + (type.equals("BAN") ? "banned." : "muted."));
                return;
            }
            if (type.equals("MUTE")) {
                List<Punishments.Entry> warns = pl.punishments.active(t.getUniqueId(), "WARN");
                if (!warns.isEmpty()) {
                    Util.msg(s, "&e" + name + " has &c" + warns.size() + " active warn(s)&e:");
                    for (Punishments.Entry w : warns) {
                        s.sendMessage(Util.c("  &8- &f" + w.reason() + " &8(by " + w.staff() + ", "
                                + Util.fmtDate(w.created()) + ", expires in " + w.remaining() + ")"));
                    }
                }
            }
            Punishments.Entry e = pl.punishments.add(t.getUniqueId(), name, type, reason, staff, fdur);
            String dtxt = e.expires() == 0 ? "permanent" : Util.fmtDuration(fdur);
            Bukkit.broadcast(Util.c(Util.PREFIX + "&f" + name + " &7was &c" + type.toLowerCase()
                    + (type.equals("BAN") ? "ned" : type.equals("MUTE") ? "d" : "ed")
                    + " &7by &f" + staff + " &8| &7" + dtxt + " &8| &7" + reason), "admintools." + type.toLowerCase());

            pl.sync(() -> {
                Player o = t.getPlayer();
                if (o == null) return;
                switch (type) {
                    case "BAN" -> o.kick(Util.c("&c&lYou have been banned\n\n&7Reason: &f" + reason
                            + "\n&7Expires in: &f" + e.remaining() + "\n&7By: &f" + staff));
                    case "MUTE" -> Util.msg(o, "&cYou have been muted for &f" + dtxt + "&c. Reason: &f" + reason);
                    default -> Util.msg(o, "&eYou received a warning (7 days). Reason: &f" + reason);
                }
            });
        });
        return true;
    }

    private boolean lift(CommandSender s, String[] a, String type) {
        if (a.length < 1) return false;
        OfflinePlayer t = known(s, a[0]);
        if (t == null) return true;
        pl.async(() -> {
            if (pl.punishments.active(t.getUniqueId(), type).isEmpty()) {
                Util.msg(s, "&c" + a[0] + " has no active " + type.toLowerCase() + ".");
                return;
            }
            pl.punishments.deactivate(t.getUniqueId(), type);
            Util.msg(s, "&a" + a[0] + " is no longer " + (type.equals("BAN") ? "banned." : "muted."));
            Player o = t.getPlayer();
            if (o != null && type.equals("MUTE")) Util.msg(o, "&aYou have been unmuted.");
        });
        return true;
    }

    private boolean history(CommandSender s, String[] a) {
        if (a.length < 1) return false;
        OfflinePlayer t = known(s, a[0]);
        if (t == null) return true;
        pl.async(() -> {
            List<Punishments.Entry> all = pl.punishments.history(t.getUniqueId());
            if (all.isEmpty()) { Util.msg(s, "&a" + a[0] + " has a clean history."); return; }
            long bans = all.stream().filter(e -> e.type().equals("BAN")).count();
            long mutes = all.stream().filter(e -> e.type().equals("MUTE")).count();
            long warns = all.stream().filter(e -> e.type().equals("WARN")).count();
            long activeWarns = all.stream().filter(e -> e.type().equals("WARN") && e.isActive()).count();
            s.sendMessage(Util.c("&6&lHistory of " + a[0]));
            s.sendMessage(Util.c("&cBans: &f" + bans + " &8| &6Mutes: &f" + mutes + " &8| &eWarns: &f" + warns
                    + " &7(active: &f" + activeWarns + "&7)"));
            int shown = 0;
            for (Punishments.Entry e : all) {
                if (shown++ >= 15) { s.sendMessage(Util.c("&8... and " + (all.size() - 15) + " older entries")); break; }
                String color = e.type().equals("BAN") ? "&c" : e.type().equals("MUTE") ? "&6" : "&e";
                String dur = e.expires() == 0 ? "permanent" : Util.fmtDuration(e.expires() - e.created());
                s.sendMessage(Util.c("&8#" + e.id() + " " + color + e.type() + " &f" + e.reason()
                        + " &8| by " + e.staff() + " | " + Util.fmtDate(e.created()) + " | " + dur + " | "
                        + (e.isActive() ? "&aACTIVE" : "&7ended")));
            }
        });
        return true;
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            if (n.equals("vanish")) { out.add("on"); out.add("off"); }
            else if (n.equals("tppos")) out.add("~");
            else if (!n.equals("log") || !a[0].matches("\\d+")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (pl.vanish.isVanished(p.getUniqueId()) && !pl.vanish.canSee(s)) continue;
                    out.add(p.getName());
                }
            }
        } else if (a.length == 2 && (n.equals("mute") || n.equals("ban"))) {
            out.addAll(List.of("1s", "1m", "1h", "1d", "perm"));
        } else if ((n.equals("tppos")) && a.length <= 3) {
            out.add("~");
        } else if (n.equals("tppos") && a.length == 4) {
            Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}

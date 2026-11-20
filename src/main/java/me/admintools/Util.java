package me.admintools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern TIME = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    public static final String PREFIX = "&8[&bAdmin&8] &7";

    private Util() {}

    public static Component c(String s) { return LEG.deserialize(s); }

    public static void msg(CommandSender s, String m) { s.sendMessage(c(PREFIX + m)); }

    /** Returns millis, 0 for permanent, -1 for invalid. Accepts 10s 5m 2h 7d or perm. */
    public static long parseDuration(String in) {
        if (in.equalsIgnoreCase("perm") || in.equalsIgnoreCase("permanent")) return 0;
        Matcher m = TIME.matcher(in);
        if (!m.matches()) return -1;
        long v;
        try { v = Long.parseLong(m.group(1)); } catch (NumberFormatException e) { return -1; }
        long unit = switch (Character.toLowerCase(m.group(2).charAt(0))) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            default -> 86_400_000L;
        };
        long r = v * unit;
        return (v <= 0 || r / unit != v) ? -1 : r;
    }

    public static String fmtDuration(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        if (s > 0 && d == 0) b.append(s).append("s");
        return b.toString().trim();
    }

    public static String fmtDate(long ms) { return FMT.format(java.time.Instant.ofEpochMilli(ms)); }

    /** Online player or a player that has joined this server before. */
    public static OfflinePlayer findPlayer(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        return Bukkit.getOfflinePlayerIfCached(name);
    }
}

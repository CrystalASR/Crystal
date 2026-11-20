package me.admintools;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

/** Hides and blocks every "plugin:command" style command. */
public final class CommandGuard implements Listener {
    private static final String BYPASS = "admintools.colon.bypass";

    @EventHandler
    public void onSend(PlayerCommandSendEvent e) {
        if (e.getPlayer().hasPermission(BYPASS)) return;
        e.getCommands().removeIf(c -> c.contains(":"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTab(AsyncTabCompleteEvent e) {
        if (e.getSender().hasPermission(BYPASS)) return;
        String buf = e.getBuffer();
        if (!buf.startsWith("/") || buf.indexOf(' ') >= 0) return; // only the command-name stage
        var list = e.completions();
        if (list.stream().anyMatch(c -> c.suggestion().contains(":"))) {
            e.completions(list.stream().filter(c -> !c.suggestion().contains(":")).toList());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getPlayer().hasPermission(BYPASS)) return;
        String first = e.getMessage().substring(1).split(" ", 2)[0];
        if (first.contains(":")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Util.c("&cUnknown or incomplete command."));
        }
    }
}

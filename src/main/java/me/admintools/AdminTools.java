package me.admintools;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AdminTools extends JavaPlugin {
    public Database db;
    public Punishments punishments;
    public VanishManager vanish;
    public FreezeManager freeze;
    public CpsManager cps;
    public LogManager logs;
    public ViewManager views;
    private ExecutorService io;

    private static final String[] COMMANDS = {
        "invsee", "endersee", "sinvsee", "sendersee", "cps", "itp", "tppos", "log", "vanish",
        "invfreeze", "invunfreeze", "freeze", "unfreeze", "mute", "unmute", "ban", "unban", "warn", "history"
    };

    @Override
    public void onEnable() {
        io = Executors.newSingleThreadExecutor(r -> new Thread(r, "AdminTools-IO"));
        try {
            getDataFolder().mkdirs();
            db = new Database(new File(getDataFolder(), "data.db"));
        } catch (Exception e) {
            getLogger().severe("Could not open database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        punishments = new Punishments(this);
        vanish = new VanishManager(this);
        freeze = new FreezeManager(this);
        cps = new CpsManager();
        logs = new LogManager(this);
        views = new ViewManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(punishments, this);
        pm.registerEvents(vanish, this);
        pm.registerEvents(freeze, this);
        pm.registerEvents(cps, this);
        pm.registerEvents(logs, this);
        pm.registerEvents(views, this);
        pm.registerEvents(new CommandGuard(), this);

        Commands handler = new Commands(this);
        for (String name : COMMANDS) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(handler);
                c.setTabCompleter(handler);
            }
        }
    }

    @Override
    public void onDisable() {
        if (io != null) {
            io.shutdown();
            try { io.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
        if (db != null) db.close();
    }

    /** Run DB/IO work off the main thread (single thread = ordered). */
    public void async(Runnable r) {
        try { io.execute(r); } catch (Exception ignored) {}
    }

    public void sync(Runnable r) {
        getServer().getScheduler().runTask(this, r);
    }
}

# AdminTools (Paper 1.21+)

Build:  mvn clean package   ->  target/AdminTools.jar  ->  put in /plugins
Needs Java 21. Data is stored in plugins/AdminTools/data.db (SQLite, bundled with Paper).

## LuckPerms nodes
admintools.invsee  admintools.endersee  admintools.sinvsee  admintools.sendersee
admintools.cps  admintools.itp  admintools.tppos  admintools.log
admintools.vanish  admintools.vanish.see  admintools.vanish.onjoin
admintools.invfreeze  admintools.freeze
admintools.mute (also /unmute)  admintools.ban (also /unban)
admintools.warn  admintools.history
admintools.colon.bypass  (allows plugin:command syntax; default: nobody)

Example: lp group moderator permission set admintools.mute true

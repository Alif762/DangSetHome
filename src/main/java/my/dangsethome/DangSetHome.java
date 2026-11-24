package my.dangsethome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class DangSetHome extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final List<String> COMMANDS =
            List.of("dsethome", "dhome", "ddelhome", "dhomes", "dhomelimit");

    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        File cfg = new File(getDataFolder(), "config.yml");
        boolean first = !cfg.exists();

        getConfig().addDefault("default-limit", 1);
        getConfig().addDefault("messages.home-set", "&aHome '&f%name%&a' set! &7(%count%/%limit%)");
        getConfig().addDefault("messages.home-updated", "&aHome '&f%name%&a' updated.");
        getConfig().addDefault("messages.limit-reached",
                "&cYou've reached your home limit (&f%limit%&c). Delete one with /ddelhome <name> or upgrade your rank.");
        getConfig().addDefault("messages.home-deleted", "&aHome '&f%name%&a' deleted.");
        getConfig().addDefault("messages.no-home", "&cYou don't have a home called '&f%name%&c'.");
        getConfig().addDefault("messages.no-homes", "&cYou don't have any homes. Set one with /dsethome <name>.");
        getConfig().addDefault("messages.teleported", "&aTeleported to home '&f%name%&a'.");
        getConfig().addDefault("messages.list-header", "&8--- &6Your Homes &7(%count%/%limit%) &8---");
        getConfig().addDefault("messages.invalid-name",
                "&cHome names can only use letters, numbers and _ (max 16 characters).");
        getConfig().addDefault("messages.world-missing", "&cThat home's world no longer exists.");
        getConfig().addDefault("messages.choose", "&eChoose a home: &f%list%");
        getConfig().addDefault("messages.no-permission", "&cYou don't have permission.");

        // Sample limits, only written the very first time
        if (first) {
            getConfig().set("limits.default", 1);
            getConfig().set("limits.vip", 3);
            getConfig().set("limits.donor", 5);
            getConfig().set("limits.helper", 5);
            getConfig().set("limits.admin", 10);
            getConfig().set("limits.owner", 50);
        }
        getConfig().options().copyDefaults(true);
        saveConfig();

        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "homes.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        for (String name : COMMANDS) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
    }

    private String msg(String key, String... repl) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < repl.length; i += 2) s = s.replace(repl[i], repl[i + 1]);
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (Exception e) {
            getLogger().warning("Could not save homes.yml: " + e.getMessage());
        }
    }

    // ---------- Limits (by LuckPerms group) ----------
    private int limitOf(Player p) {
        if (p.hasPermission("dangsethome.unlimited")) return Integer.MAX_VALUE;
        int best = getConfig().getInt("default-limit", 1);
        boolean matched = false;
        ConfigurationSection sec = getConfig().getConfigurationSection("limits");
        if (sec != null) {
            for (String g : sec.getKeys(false)) {
                if (p.hasPermission("group." + g)) {
                    int v = sec.getInt(g);
                    if (!matched || v > best) {
                        best = v;
                        matched = true;
                    }
                }
            }
        }
        return best;
    }

    private String limitText(int limit) {
        return limit == Integer.MAX_VALUE ? "unlimited" : String.valueOf(limit);
    }

    // ---------- Home data ----------
    private List<String> homeNames(UUID id) {
        ConfigurationSection s = data.getConfigurationSection("homes." + id);
        if (s == null) return new ArrayList<>();
        List<String> list = new ArrayList<>(s.getKeys(false));
        Collections.sort(list);
        return list;
    }

    // ---------- Commands ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase();
        if (c.equals("dhomelimit")) return limitCmd(sender, args);

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!p.hasPermission("dangsethome.use")) {
            p.sendMessage(msg("no-permission"));
            return true;
        }

        switch (c) {
            case "dsethome" -> setHome(p, args);
            case "dhome" -> goHome(p, args);
            case "ddelhome" -> delHome(p, args);
            case "dhomes" -> listHomes(p);
            default -> {
            }
        }
        return true;
    }

    private void setHome(Player p, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase() : "home";
        if (!name.matches("[a-z0-9_]{1,16}")) {
            p.sendMessage(msg("invalid-name"));
            return;
        }

        List<String> names = homeNames(p.getUniqueId());
        boolean exists = names.contains(name);
        int limit = limitOf(p);
        if (!exists && names.size() >= limit) {
            p.sendMessage(msg("limit-reached", "%limit%", limitText(limit)));
            return;
        }

        Location l = p.getLocation();
        String base = "homes." + p.getUniqueId() + "." + name;
        data.set(base + ".world", l.getWorld().getName());
        data.set(base + ".x", l.getX());
        data.set(base + ".y", l.getY());
        data.set(base + ".z", l.getZ());
        data.set(base + ".yaw", (double) l.getYaw());
        data.set(base + ".pitch", (double) l.getPitch());
        saveData();

        if (exists) {
            p.sendMessage(msg("home-updated", "%name%", name));
        } else {
            p.sendMessage(msg("home-set", "%name%", name,
                    "%count%", String.valueOf(names.size() + 1),
                    "%limit%", limitText(limit)));
        }
    }

    private void goHome(Player p, String[] args) {
        List<String> names = homeNames(p.getUniqueId());
        String name;
        if (args.length > 0) {
            name = args[0].toLowerCase();
        } else {
            if (names.isEmpty()) {
                p.sendMessage(msg("no-homes"));
                return;
            }
            if (names.contains("home")) {
                name = "home";
            } else if (names.size() == 1) {
                name = names.get(0);
            } else {
                p.sendMessage(msg("choose", "%list%", String.join(", ", names)));
                return;
            }
        }

        String base = "homes." + p.getUniqueId() + "." + name;
        if (!data.isConfigurationSection(base)) {
            p.sendMessage(msg("no-home", "%name%", name));
            return;
        }
        World w = Bukkit.getWorld(data.getString(base + ".world", ""));
        if (w == null) {
            p.sendMessage(msg("world-missing"));
            return;
        }
        Location loc = new Location(w,
                data.getDouble(base + ".x"),
                data.getDouble(base + ".y"),
                data.getDouble(base + ".z"),
                (float) data.getDouble(base + ".yaw"),
                (float) data.getDouble(base + ".pitch"));
        p.teleport(loc);
        p.sendMessage(msg("teleported", "%name%", name));
    }

    private void delHome(Player p, String[] args) {
        if (args.length == 0) {
            p.sendMessage(color("&eUsage: /ddelhome <name>"));
            return;
        }
        String name = args[0].toLowerCase();
        String base = "homes." + p.getUniqueId() + "." + name;
        if (!data.isConfigurationSection(base)) {
            p.sendMessage(msg("no-home", "%name%", name));
            return;
        }
        data.set(base, null);
        saveData();
        p.sendMessage(msg("home-deleted", "%name%", name));
    }

    private void listHomes(Player p) {
        List<String> names = homeNames(p.getUniqueId());
        if (names.isEmpty()) {
            p.sendMessage(msg("no-homes"));
            return;
        }
        p.sendMessage(msg("list-header",
                "%count%", String.valueOf(names.size()),
                "%limit%", limitText(limitOf(p))));
        for (String n : names) {
            String base = "homes." + p.getUniqueId() + "." + n;
            p.sendMessage(color("&e" + n + " &7- " + data.getString(base + ".world", "?")
                    + " (" + (int) data.getDouble(base + ".x") + ", "
                    + (int) data.getDouble(base + ".y") + ", "
                    + (int) data.getDouble(base + ".z") + ")"));
        }
    }

    // ---------- Admin: /dhomelimit ----------
    private boolean limitCmd(CommandSender s, String[] a) {
        if (!s.hasPermission("dangsethome.admin")) {
            s.sendMessage(msg("no-permission"));
            return true;
        }
        if (a.length == 0) {
            limitHelp(s);
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "set" -> {
                if (a.length < 3) {
                    s.sendMessage(color("&eUsage: /dhomelimit set <rank> <amount>"));
                    return true;
                }
                String g = a[1].toLowerCase();
                if (!g.matches("[a-z0-9_-]{1,32}")) {
                    s.sendMessage(color("&cInvalid rank name."));
                    return true;
                }
                int n;
                try {
                    n = Integer.parseInt(a[2]);
                } catch (NumberFormatException e) {
                    s.sendMessage(color("&cAmount must be a number."));
                    return true;
                }
                if (n < 0 || n > 1000) {
                    s.sendMessage(color("&cAmount must be between 0 and 1000."));
                    return true;
                }
                getConfig().set("limits." + g, n);
                saveConfig();
                s.sendMessage(color("&aRank &f" + g + " &acan now have &f" + n + " &ahomes."));
            }
            case "remove" -> {
                if (a.length < 2) {
                    s.sendMessage(color("&eUsage: /dhomelimit remove <rank>"));
                    return true;
                }
                String g = a[1].toLowerCase();
                if (!getConfig().isSet("limits." + g)) {
                    s.sendMessage(color("&cThat rank has no limit set."));
                    return true;
                }
                getConfig().set("limits." + g, null);
                saveConfig();
                s.sendMessage(color("&aRemoved the limit for &f" + g + "&a."));
            }
            case "list" -> {
                s.sendMessage(color("&8--- &6Home Limits &8---"));
                ConfigurationSection sec = getConfig().getConfigurationSection("limits");
                List<String> rows = new ArrayList<>();
                if (sec != null) {
                    for (String g : sec.getKeys(false)) rows.add(g);
                }
                rows.sort((x, y) -> Integer.compare(sec.getInt(y), sec.getInt(x)));
                for (String g : rows) {
                    s.sendMessage(color("&e" + g + " &7- &f" + sec.getInt(g)));
                }
                s.sendMessage(color("&7Players with no matching rank: &f"
                        + getConfig().getInt("default-limit", 1)));
            }
            case "reload" -> {
                reloadConfig();
                s.sendMessage(color("&aConfig reloaded."));
            }
            default -> limitHelp(s);
        }
        return true;
    }

    private void limitHelp(CommandSender s) {
        s.sendMessage(color("&8--- &6DangSetHome Admin &8---"));
        s.sendMessage(color("&e/dhomelimit set <rank> <amount> &7- set how many homes a rank gets"));
        s.sendMessage(color("&e/dhomelimit remove <rank> &7- remove a rank's limit"));
        s.sendMessage(color("&e/dhomelimit list &7- show all limits"));
        s.sendMessage(color("&e/dhomelimit reload &7- reload the config"));
        s.sendMessage(color("&7Rank = the LuckPerms group name (see /lp listgroups)."));
    }

    // ---------- Tab complete ----------
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        String n = c.getName().toLowerCase();

        if ((n.equals("dhome") || n.equals("ddelhome")) && a.length == 1 && s instanceof Player p) {
            out.addAll(homeNames(p.getUniqueId()));
        } else if (n.equals("dhomelimit") && s.hasPermission("dangsethome.admin")) {
            if (a.length == 1) {
                out.addAll(List.of("set", "remove", "list", "reload"));
            } else if (a.length == 2
                    && (a[0].equalsIgnoreCase("set") || a[0].equalsIgnoreCase("remove"))) {
                ConfigurationSection sec = getConfig().getConfigurationSection("limits");
                if (sec != null) out.addAll(sec.getKeys(false));
            }
        }

        String prefix = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        List<String> filtered = new ArrayList<>();
        for (String x : out) {
            if (x.toLowerCase().startsWith(prefix)) filtered.add(x);
        }
        return filtered;
    }
          }

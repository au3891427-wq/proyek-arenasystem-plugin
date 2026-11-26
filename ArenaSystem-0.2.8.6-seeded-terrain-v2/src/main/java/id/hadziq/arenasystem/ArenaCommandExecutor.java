package id.hadziq.arenasystem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Router perintah "/arena ...". Hanya berisi parsing argumen dan dispatch;
 * seluruh logic arena/match tetap berada di {@link ArenaSystemPlugin} (mesin
 * plugin) supaya perubahan parsing command tidak menyentuh business logic,
 * dan sebaliknya.
 */
final class ArenaCommandExecutor implements CommandExecutor {
    private final ArenaSystemPlugin plugin;

    ArenaCommandExecutor(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
        plugin.sendHelp(sender);
        return true;
    }

    String first = args[0].toLowerCase(Locale.ROOT);
    if (first.equals("list")) {
        plugin.listArenas(sender);
        return true;
    }
    if (plugin.isProtectionAction(first)) {
        plugin.setProtectionFromCommand(sender, null, first, args, 1);
        return true;
    }
    if (first.equals("team")) {
        if (!plugin.requireAdmin(sender)) return true;
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena team <nama> [2|3|4]");
            return true;
        }
        plugin.setArenaTeamCount(sender, args[1], args.length == 3 ? args[2] : "2");
        return true;
    }
    if (first.equals("class")) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Menu class hanya dapat dibuka oleh pemain.");
            return true;
        }
        if (args.length >= 2) {
            plugin.selectClassByName(player, args[1]);
        } else {
            plugin.openClassMenuFor(player);
        }
        return true;
    }
    if (first.equals("join")) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena join <nama> <red|blue|green|yellow> [namaPlayer]");
            return true;
        }
        if (args.length >= 4) {
            if (!plugin.requireAdmin(sender)) return true;
            Player target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Pemain " + args[3] + " tidak ditemukan atau sedang offline.");
                return true;
            }
            plugin.joinArena(target, args[1], args[2]);
            sender.sendMessage(ChatColor.GREEN + "Berhasil memasukkan " + target.getName() + " ke tim " + args[2].toUpperCase(Locale.ROOT) + " di arena " + args[1] + ".");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah join mandiri hanya dapat digunakan oleh pemain.");
            return true;
        }
        plugin.joinArena((Player) sender, args[1], args[2]);
        return true;
    }
    if (first.equals("leave")) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah leave hanya dapat digunakan pemain.");
            return true;
        }
        plugin.leaveArena((Player) sender);
        return true;
    }
    if (first.equals("tp")) {
        plugin.teleportToArena(sender, args);
        return true;
    }
    if (first.equals("info")) {
        plugin.showInfo(sender, args);
        return true;
    }
    if (first.equals("mode")) {
        if (!plugin.requireAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena mode <nama> <tdm|relic_rush>");
            return true;
        }
        plugin.setMode(sender, args[1], args[2]);
        return true;
    }
    if (first.equals("start") || first.equals("stop")) {
        if (!plugin.requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena " + first + " <nama>");
            return true;
        }
        if (first.equals("start")) plugin.startMatch(sender, args[1]); else plugin.stopMatch(sender, args[1], "Pertandingan dihentikan admin.");
        return true;
    }
    if (first.equals("create")) {
        if (!plugin.requireAdmin(sender)) return true;
        plugin.createArena(sender, args);
        return true;
    }
    if (first.equals("delete")) {
        if (!plugin.requireAdmin(sender)) return true;
        plugin.deleteArena(sender, args);
        return true;
    }
    if (first.equals("reset")) {
        if (!plugin.requireAdmin(sender)) return true;
        plugin.resetMatch(sender, args);
        return true;
    }
    if (first.equals("regenerate")) {
        if (!plugin.requireAdmin(sender)) return true;
        plugin.regenerateArena(sender, args);
        return true;
    }
    if (first.equals("quarantine")) {
        if (!plugin.requireAdmin(sender)) return true;
        plugin.quarantineCommand(sender, args);
        return true;
    }
    if (first.equals("recover")) {
        if (!plugin.requireAdmin(sender)) return true;
        plugin.recoverArena(sender, args);
        return true;
    }
    if (first.equals("clearmobs")) {
        if (!plugin.requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena clearmobs <nama>");
            return true;
        }
        ArenaData arena = plugin.arenas.get(args[1].toLowerCase(Locale.ROOT));
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
            return true;
        }
        plugin.clearMobsInArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Semua mob di dalam area arena " + arena.name + " telah dibersihkan!");
        return true;
    }
    if (first.equals("clearvegetation")) {
        if (!plugin.requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena clearvegetation <nama>");
            return true;
        }
        plugin.clearArenaVegetation(sender, args[1]);
        return true;
    }

    ArenaData arena = plugin.arenas.get(first);
    if (arena != null && args.length >= 2) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if (plugin.isProtectionAction(action)) {
            plugin.setProtectionFromCommand(sender, arena.name, action, args, 2);
            return true;
        }
        if (action.equals("clearmobs")) {
            if (!plugin.requireAdmin(sender)) return true;
            plugin.clearMobsInArena(arena);
            sender.sendMessage(ChatColor.GREEN + "Semua mob di dalam area arena " + arena.name + " telah dibersihkan!");
            return true;
        }
        if (action.equals("clearvegetation")) {
            if (!plugin.requireAdmin(sender)) return true;
            plugin.clearArenaVegetation(sender, arena.name);
            return true;
        }
        if (action.equals("mode")) {
            if (!plugin.requireAdmin(sender)) return true;
            if (args.length < 3) sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena " + arena.name + " mode <tdm|relic_rush>");
            else plugin.setMode(sender, arena.name, args[2]);
            return true;
        }
        if (action.equals("join")) {
            if (args.length >= 4) {
                if (!plugin.requireAdmin(sender)) return true;
                Player target = Bukkit.getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Pemain " + args[3] + " tidak ditemukan atau sedang offline.");
                    return true;
                }
                plugin.joinArena(target, arena.name, args[2]);
                sender.sendMessage(ChatColor.GREEN + "Berhasil memasukkan " + target.getName() + " ke tim " + args[2].toUpperCase(Locale.ROOT) + " di arena " + arena.name + ".");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Perintah join mandiri hanya dapat digunakan oleh pemain.");
            } else if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena " + arena.name + " join <red|blue|green|yellow>");
            } else {
                plugin.joinArena((Player) sender, arena.name, args[2]);
            }
            return true;
        }
        if (action.equals("start") || action.equals("stop")) {
            if (!plugin.requireAdmin(sender)) return true;
            if (action.equals("start")) plugin.startMatch(sender, arena.name);
            else plugin.stopMatch(sender, arena.name, "Pertandingan dihentikan admin.");
            return true;
        }
        if (action.equals("tp")) {
            if (args.length < 3) sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena " + arena.name + " tp <red|blue|green|yellow|center>");
            else plugin.teleportToArena(sender, new String[]{"tp", arena.name, args[2]});
            return true;
        }
        if (action.equals("info")) {
            plugin.showInfo(sender, new String[]{"info", arena.name});
            return true;
        }
        if (action.equals("reset")) {
            if (!plugin.requireAdmin(sender)) return true;
            plugin.resetMatch(sender, new String[]{"reset", arena.name});
            return true;
        }
        if (action.equals("delete")) {
            if (!plugin.requireAdmin(sender)) return true;
            plugin.deleteArena(sender, new String[]{"delete", arena.name, args.length >= 3 ? args[2] : ""});
            return true;
        }
        if (action.equals("regenerate")) {
            if (!plugin.requireAdmin(sender)) return true;
            plugin.regenerateArena(sender, new String[]{"regenerate", arena.name, args.length >= 3 ? args[2] : ""});
            return true;
        }
    }

    plugin.sendHelp(sender);
    return true;
}
}

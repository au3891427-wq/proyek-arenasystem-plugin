package id.hadziq.arenasystem;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// $VF: Compiled from ArenaCommandExecutor.java
final class ArenaCommandExecutor implements CommandExecutor {
    private final ArenaSystemPlugin plugin;

    ArenaCommandExecutor(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0 && !args[0].equalsIgnoreCase("help")) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("list")) {
                this.plugin.listArenas(sender);
                return true;
            }

            if (this.plugin.isProtectionAction(first)) {
                this.plugin.setProtectionFromCommand(sender, null, first, args, 1);
                return true;
            }

            if (first.equals("team")) {
                if (!this.plugin.requireAdmin(sender)) {
                    return true;
                } else if (args.length >= 2 && args.length <= 3) {
                    this.plugin.setArenaTeamCount(sender, args[1], args.length == 3 ? args[2] : "2");
                    return true;
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena team <nama> [2|3|4]");
                    return true;
                }
            } else if (first.equals("class")) {
                if (sender instanceof Player player) {
                    if (args.length >= 2) {
                        this.plugin.selectClassByName(player, args[1]);
                    } else {
                        this.plugin.openClassMenuFor(player);
                    }

                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "Menu class hanya dapat dibuka oleh pemain.");
                    return true;
                }
            } else if (first.equals("join")) {
                if (args.length < 3) {
                    sender.sendMessage(
                        ChatColor.YELLOW + "Gunakan: /arena join <nama> <red|blue|green|yellow> [namaPlayer]"
                    );
                    return true;
                }

                if (args.length >= 4) {
                    if (!this.plugin.requireAdmin(sender)) {
                        return true;
                    } else {
                        Player target = Bukkit.getPlayer(args[3]);
                        if (target == null) {
                            sender.sendMessage(
                                ChatColor.RED + "Pemain " + args[3] + " tidak ditemukan atau sedang offline."
                            );
                            return true;
                        } else {
                            this.plugin.joinArena(target, args[1], args[2]);
                            sender.sendMessage(
                                ChatColor.GREEN
                                    + "Berhasil memasukkan "
                                    + target.getName()
                                    + " ke tim "
                                    + args[2].toUpperCase(Locale.ROOT)
                                    + " di arena "
                                    + args[1]
                                    + "."
                            );
                            return true;
                        }
                    }
                } else if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Perintah join mandiri hanya dapat digunakan oleh pemain.");
                    return true;
                } else {
                    this.plugin.joinArena((Player)sender, args[1], args[2]);
                    return true;
                }
            } else if (first.equals("leave")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Perintah leave hanya dapat digunakan pemain.");
                    return true;
                } else {
                    this.plugin.leaveArena((Player)sender);
                    return true;
                }
            } else {
                if (first.equals("tp")) {
                    this.plugin.teleportToArena(sender, args);
                    return true;
                }

                if (first.equals("info")) {
                    this.plugin.showInfo(sender, args);
                    return true;
                }

                if (first.equals("mode")) {
                    if (!this.plugin.requireAdmin(sender)) {
                        return true;
                    } else if (args.length < 3) {
                        sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena mode <nama> <tdm|relic_rush>");
                        return true;
                    } else {
                        this.plugin.setMode(sender, args[1], args[2]);
                        return true;
                    }
                } else if (!first.equals("start") && !first.equals("stop")) {
                    if (first.equals("create")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        }

                        this.plugin.createArena(sender, args);
                        return true;
                    } else if (first.equals("delete")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        }

                        this.plugin.deleteArena(sender, args);
                        return true;
                    } else if (first.equals("reset")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        }

                        this.plugin.resetMatch(sender, args);
                        return true;
                    } else if (first.equals("regenerate")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        }

                        this.plugin.regenerateArena(sender, args);
                        return true;
                    } else if (first.equals("quarantine")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        }

                        this.plugin.quarantineCommand(sender, args);
                        return true;
                    } else if (first.equals("recover")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        }

                        this.plugin.recoverArena(sender, args);
                        return true;
                    } else if (first.equals("clearmobs")) {
                        if (!this.plugin.requireAdmin(sender)) {
                            return true;
                        } else if (args.length < 2) {
                            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena clearmobs <nama>");
                            return true;
                        } else {
                            ArenaData arena = this.plugin.arenas.get(args[1].toLowerCase(Locale.ROOT));
                            if (arena == null) {
                                sender.sendMessage(ChatColor.RED + "Arena tidak ditemukan.");
                                return true;
                            } else {
                                this.plugin.clearMobsInArena(arena);
                                sender.sendMessage(
                                    ChatColor.GREEN
                                        + "Semua mob di dalam area arena "
                                        + arena.name
                                        + " telah dibersihkan!"
                                );
                                return true;
                            }
                        }
                    } else if (!first.equals("clearvegetation")) {
                        ArenaData arena = this.plugin.arenas.get(first);
                        if (arena != null && args.length >= 2) {
                            String action = args[1].toLowerCase(Locale.ROOT);
                            if (this.plugin.isProtectionAction(action)) {
                                this.plugin.setProtectionFromCommand(sender, arena.name, action, args, 2);
                                return true;
                            }

                            if (action.equals("clearmobs")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                this.plugin.clearMobsInArena(arena);
                                sender.sendMessage(
                                    ChatColor.GREEN
                                        + "Semua mob di dalam area arena "
                                        + arena.name
                                        + " telah dibersihkan!"
                                );
                                return true;
                            }

                            if (action.equals("clearvegetation")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                this.plugin.clearArenaVegetation(sender, arena.name);
                                return true;
                            }

                            if (action.equals("mode")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                if (args.length < 3) {
                                    sender.sendMessage(
                                        ChatColor.YELLOW + "Gunakan: /arena " + arena.name + " mode <tdm|relic_rush>"
                                    );
                                } else {
                                    this.plugin.setMode(sender, arena.name, args[2]);
                                }

                                return true;
                            }

                            if (action.equals("join")) {
                                if (args.length >= 4) {
                                    if (!this.plugin.requireAdmin(sender)) {
                                        return true;
                                    }

                                    Player target = Bukkit.getPlayer(args[3]);
                                    if (target == null) {
                                        sender.sendMessage(
                                            ChatColor.RED
                                                + "Pemain "
                                                + args[3]
                                                + " tidak ditemukan atau sedang offline."
                                        );
                                        return true;
                                    }

                                    this.plugin.joinArena(target, arena.name, args[2]);
                                    sender.sendMessage(
                                        ChatColor.GREEN
                                            + "Berhasil memasukkan "
                                            + target.getName()
                                            + " ke tim "
                                            + args[2].toUpperCase(Locale.ROOT)
                                            + " di arena "
                                            + arena.name
                                            + "."
                                    );
                                    return true;
                                }

                                if (!(sender instanceof Player)) {
                                    sender.sendMessage(
                                        ChatColor.RED + "Perintah join mandiri hanya dapat digunakan oleh pemain."
                                    );
                                } else if (args.length < 3) {
                                    sender.sendMessage(
                                        ChatColor.YELLOW
                                            + "Gunakan: /arena "
                                            + arena.name
                                            + " join <red|blue|green|yellow>"
                                    );
                                } else {
                                    this.plugin.joinArena((Player)sender, arena.name, args[2]);
                                }

                                return true;
                            }

                            if (action.equals("start") || action.equals("stop")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                if (action.equals("start")) {
                                    this.plugin.startMatch(sender, arena.name);
                                } else {
                                    this.plugin.stopMatch(sender, arena.name, "Pertandingan dihentikan admin.");
                                }

                                return true;
                            }

                            if (action.equals("tp")) {
                                if (args.length < 3) {
                                    sender.sendMessage(
                                        ChatColor.YELLOW
                                            + "Gunakan: /arena "
                                            + arena.name
                                            + " tp <red|blue|green|yellow|center>"
                                    );
                                } else {
                                    this.plugin.teleportToArena(sender, new String[]{"tp", arena.name, args[2]});
                                }

                                return true;
                            }

                            if (action.equals("info")) {
                                this.plugin.showInfo(sender, new String[]{"info", arena.name});
                                return true;
                            }

                            if (action.equals("reset")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                this.plugin.resetMatch(sender, new String[]{"reset", arena.name});
                                return true;
                            }

                            if (action.equals("delete")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                this.plugin
                                    .deleteArena(
                                        sender, new String[]{"delete", arena.name, args.length >= 3 ? args[2] : ""}
                                    );
                                return true;
                            }

                            if (action.equals("regenerate")) {
                                if (!this.plugin.requireAdmin(sender)) {
                                    return true;
                                }

                                this.plugin
                                    .regenerateArena(
                                        sender, new String[]{"regenerate", arena.name, args.length >= 3 ? args[2] : ""}
                                    );
                                return true;
                            }
                        }

                        this.plugin.sendHelp(sender);
                        return true;
                    } else if (!this.plugin.requireAdmin(sender)) {
                        return true;
                    } else if (args.length < 2) {
                        sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena clearvegetation <nama>");
                        return true;
                    } else {
                        this.plugin.clearArenaVegetation(sender, args[1]);
                        return true;
                    }
                } else {
                    if (!this.plugin.requireAdmin(sender)) {
                        return true;
                    }

                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.YELLOW + "Gunakan: /arena " + first + " <nama>");
                        return true;
                    }

                    if (first.equals("start")) {
                        this.plugin.startMatch(sender, args[1]);
                    } else {
                        this.plugin.stopMatch(sender, args[1], "Pertandingan dihentikan admin.");
                    }

                    return true;
                }
            }
        } else {
            this.plugin.sendHelp(sender);
            return true;
        }
    }
}

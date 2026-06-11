package dev.gdlev.spillNDrop;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

public final class SpillNDrop extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Random random = new Random();
    private FileConfiguration lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLang();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("spillndrop")).setExecutor(this);
        Objects.requireNonNull(getCommand("spillndrop")).setTabCompleter(this);
        getLogger().info("SpillNDrop has been enabled!");
    }

    private void loadLang() {
        String langCode = getConfig().getString("language", "en");
        File langFolder = new File(getDataFolder(), "langs");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, langCode + ".yml");
        if (!langFile.exists()) {
            // If file doesn't exist in folder, try to copy it from resources
            saveResource("langs/" + langCode + ".yml", false);
        }

        // Final check if copied successfully or exists
        if (langFile.exists()) {
            lang = YamlConfiguration.loadConfiguration(langFile);
        } else {
            // Fallback to basic messages if something went wrong
            lang = new YamlConfiguration();
            getLogger().warning("Could not load language file: " + langCode + ".yml! Using fallback.");
        }
    }

    public String getMsg(String path) {
        String msg = lang.getString(path, path);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!isEnabledWorld(victim.getWorld().getName())) return;

        String configKey = null;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            configKey = "FALL";
        } else if (event.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) {
            configKey = "SONIC_BOOM";
        } else if (event instanceof EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Goat) {
                configKey = "GOAT_ATTACK";
            }
        }

        if (configKey == null || !getConfig().contains("multipliers." + configKey)) return;

        boolean dropItems = getConfig().getBoolean("features.drop-items", true);
        boolean dropHeldItem = getConfig().getBoolean("features.drop-held-item", false);

        // Neither mode is enabled — nothing to do
        if (!dropItems && !dropHeldItem) return;

        double dropChance = calculateChance(configKey, event.getFinalDamage());
        double heightOffset = getConfig().getDouble("drop-height-offset", 0.5);
        Location dropLoc = victim.getLocation().add(0, heightOffset, 0);

        if (dropHeldItem) {
            // Drop the item currently held in the main hand
            ItemStack heldItem = victim.getInventory().getItemInMainHand();
            if (heldItem.getType() != Material.AIR) {
                if (random.nextInt(100) + 1 <= dropChance) {
                    int heldSlot = victim.getInventory().getHeldItemSlot();
                    handleItemSpill(victim, heldSlot, heldItem, dropLoc);
                }
            }
        }

        if (dropItems) {
            // Iterate entire inventory
            ItemStack[] contents = victim.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null || item.getType() == Material.AIR) continue;

                if (random.nextInt(100) + 1 <= dropChance) {
                    handleItemSpill(victim, i, item, dropLoc);
                }
            }
        }
    }

    private boolean isEnabledWorld(String worldName) {
        if (!getConfig().contains("enabled-worlds")) {
            return worldName.equalsIgnoreCase("world")
                    || worldName.equalsIgnoreCase("world_nether")
                    || worldName.equalsIgnoreCase("world_the_end");
        }

        return getConfig().getStringList("enabled-worlds").stream()
                .anyMatch(configuredWorld -> configuredWorld.equals("*")
                        || configuredWorld.equalsIgnoreCase(worldName));
    }

    private double calculateChance(String key, double damage) {
        String value = getConfig().getString("multipliers." + key);
        if (value == null) return 0;

        double chance;
        if (value.endsWith("%")) {
            try {
                chance = Double.parseDouble(value.replace("%", ""));
            } catch (NumberFormatException e) { chance = 0; }
        } else {
            try {
                chance = damage * Double.parseDouble(value);
            } catch (NumberFormatException e) { chance = 0; }
        }
        return Math.min(chance, 100.0);
    }

    private void handleItemSpill(Player victim, int slot, ItemStack item, Location loc) {
        Material type = item.getType();
        double scatter = getConfig().getDouble("scatter-force", 0.2);
        Vector scatterVel = new Vector(
                (random.nextDouble() - 0.5) * scatter,
                random.nextDouble() * scatter,
                (random.nextDouble() - 0.5) * scatter
        );

        boolean isWaterBucket = type == Material.WATER_BUCKET;
        boolean isPotion = type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;

        if (isWaterBucket && getConfig().getBoolean("features.spill-water", true)) {
            victim.getInventory().setItem(slot, new ItemStack(Material.BUCKET));
            loc.getBlock().setType(Material.WATER);
            Objects.requireNonNull(loc.getWorld()).playSound(loc, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
        } else if (isPotion && getConfig().getBoolean("features.smash-potions", true)) {
            ItemStack singlePotion = item.clone();
            singlePotion.setAmount(1);
            if (singlePotion.getType() == Material.POTION) {
                singlePotion.setType(Material.SPLASH_POTION);
            }
            EntityType potionEntityType = EntityType.SPLASH_POTION;
            if (singlePotion.getType() == Material.LINGERING_POTION) {
                try {
                    potionEntityType = EntityType.valueOf("LINGERING_POTION");
                } catch (IllegalArgumentException ignored) {
                    // Older server versions use SPLASH_POTION for both thrown potion variants.
                }
            }
            ThrownPotion thrown = (ThrownPotion) Objects.requireNonNull(loc.getWorld()).spawnEntity(loc, potionEntityType);
            thrown.setItem(singlePotion);
            thrown.setVelocity(scatterVel);
            removeItem(victim, slot);
        } else {
            Item dropped = Objects.requireNonNull(loc.getWorld()).dropItemNaturally(loc, item.clone());
            dropped.setVelocity(dropped.getVelocity().add(scatterVel));
            dropped.setPickupDelay(getConfig().getInt("pickup-delay", 20));
            removeItem(victim, slot);
        }
    }

    private void removeItem(Player victim, int slot) {
        // If an item stack is chosen to be dropped, remove the entire stack from the inventory.
        victim.getInventory().setItem(slot, null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spillndrop.admin")) {
            sender.sendMessage(getMsg("prefix") + getMsg("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(getMsg("help-header"));
            sender.sendMessage(getMsg("help-line"));
            sender.sendMessage(getMsg("help-toggle"));
            sender.sendMessage(getMsg("help-reload"));
            sender.sendMessage(getMsg("help-version"));
            sender.sendMessage(getMsg("help-footer"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadLang();
            sender.sendMessage(getMsg("prefix") + getMsg("reload-success"));
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            String msg = getMsg("version-info")
                    .replace("{version}", getDescription().getVersion())
                    .replace("{author}", String.join(", ", getDescription().getAuthors()));
            sender.sendMessage(getMsg("prefix") + msg);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                sender.sendMessage(getMsg("prefix") + getMsg("invalid-usage").replace("<subcommand>", "set <cause> <value>"));
                return true;
            }
            String cause = args[1].toUpperCase();
            String value = args[2];

            if (!getConfig().contains("multipliers." + cause)) {
                sender.sendMessage(getMsg("prefix") + getMsg("cause-not-found"));
                return true;
            }

            if (value.endsWith("%")) {
                try {
                    double pct = Double.parseDouble(value.replace("%", ""));
                    if (pct > 100) {
                        sender.sendMessage(getMsg("prefix") + getMsg("value-too-high"));
                        return true;
                    }
                    if (pct < 0) {
                        sender.sendMessage(getMsg("prefix") + getMsg("invalid-value"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMsg("prefix") + getMsg("invalid-value"));
                    return true;
                }
            } else {
                try {
                    if (Double.parseDouble(value) < 0) {
                        sender.sendMessage(getMsg("prefix") + getMsg("invalid-value"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMsg("prefix") + getMsg("invalid-value"));
                    return true;
                }
            }

            getConfig().set("multipliers." + cause, value);
            saveConfig();
            sender.sendMessage(getMsg("prefix") + getMsg("set-success")
                    .replace("{cause}", cause)
                    .replace("{value}", value));
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            if (args.length < 2) {
                sender.sendMessage(getMsg("prefix") + getMsg("invalid-usage").replace("<subcommand>", "toggle <feature> [true/false]"));
                return true;
            }
            String feature = args[1].toLowerCase();
            if (!getConfig().contains("features." + feature)) {
                sender.sendMessage(getMsg("prefix") + getMsg("feature-not-found"));
                return true;
            }

            boolean newValue;
            if (args.length >= 3) {
                newValue = Boolean.parseBoolean(args[2]);
            } else {
                // Toggle current value if not specified
                newValue = !getConfig().getBoolean("features." + feature);
            }

            getConfig().set("features." + feature, newValue);
            saveConfig();
            sender.sendMessage(getMsg("prefix") + getMsg("toggle-success")
                    .replace("{feature}", feature)
                    .replace("{value}", String.valueOf(newValue)));
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("help");
            list.add("reload");
            list.add("version");
            list.add("set");
            list.add("toggle");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            ConfigurationSection section = getConfig().getConfigurationSection("multipliers");
            if (section != null) {
                list.addAll(section.getKeys(false));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            list.add("5.0");
            list.add("20%");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            list.add("drop-items");
            list.add("drop-held-item");
            list.add("spill-water");
            list.add("smash-potions");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            list.add("true");
            list.add("false");
        }
        String search = args[args.length - 1].toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(search)).collect(Collectors.toList());
    }
}

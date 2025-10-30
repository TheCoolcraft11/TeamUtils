package de.thecoolcraft11.teamUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.scoreboard.Team;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class TeamUtils extends JavaPlugin implements Listener {
    
    private final Map<String, Inventory> teamBackpacks = new HashMap<>();
    
    private static final Component BUNDLE_NAME = Component.text("Team Backpack", NamedTextColor.GOLD);
    
    private NamespacedKey bundleKey;

    private static final String PERM_BGIVE = "teamutils.bgive";
    private static final String PERM_TOGGLE = "teamutils.toggle";
    private int ensureTaskId = -1;

    
    private boolean globallyEnabled = true;
    private final Set<String> disabledTeams = new HashSet<>();

    @Override
    public void onEnable() {
        bundleKey = new NamespacedKey(this, "team_bundle");
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        if (getCommand("p") != null) {
            Objects.requireNonNull(getCommand("p")).setExecutor(new PublicChatCommand());
        }
        if (getCommand("bgive") != null) {
            Objects.requireNonNull(getCommand("bgive")).setExecutor(new BundleGiveCommand());
        }
        if (getCommand("teamutils") != null) {
            Objects.requireNonNull(getCommand("teamutils")).setExecutor(new ToggleCommand());
        }
        
        ensureTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ensurePlayerHasBundle(p);
            }
        }, 100L, 200L); 
    }

    @Override
    public void onDisable() {
        teamBackpacks.clear();
        if (ensureTaskId != -1) {
            Bukkit.getScheduler().cancelTask(ensureTaskId);
        }
    }

    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ensurePlayerHasBundle(event.getPlayer());
    }

    

    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!globallyEnabled) return;
        Player player = event.getPlayer();
        Team team = getPlayerTeam(player);
        if (team != null && disabledTeams.contains(team.getName())) return;
        if (team != null) {
            event.setCancelled(true);
            Component msg = Component.text("[Team] ", NamedTextColor.AQUA)
                    .append(player.displayName())
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(Component.text(event.getMessage(), NamedTextColor.WHITE));
            for (String entry : team.getEntries()) {
                Player teammate = Bukkit.getPlayer(entry);
                if (teammate != null && teammate.isOnline()) {
                    teammate.sendMessage(msg);
                }
            }
        }
    }

    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!globallyEnabled) return;
        ItemStack used = event.getItem();
        if (isTeamBundle(used)) {
            Player player = event.getPlayer();
            Team team = getPlayerTeam(player);
            if (team != null && disabledTeams.contains(team.getName())) {
                player.sendMessage(Component.text("Team utilities are disabled for your team.", NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
            if (team != null) {
                event.setCancelled(true);
                Inventory inv = teamBackpacks.computeIfAbsent(team.getName(), k -> Bukkit.createInventory(null, 54, BUNDLE_NAME));
                player.openInventory(inv);
            }
        }
    }

    
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isTeamBundle(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You can't drop the Team Backpack.", NamedTextColor.RED));
        }
    }

    
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        drops.removeIf(this::isTeamBundle);
    }

    
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this, () -> ensurePlayerHasBundle(event.getPlayer()));
    }

    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean currentBundle = isTeamBundle(current);
        boolean cursorBundle = isTeamBundle(cursor);

        if (!currentBundle && !cursorBundle) return;

        
        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        
        if (clickedInv == player.getInventory()) {
            
            
            if (event.getClick().isShiftClick() && topInv.getHolder() != player) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can't move the Team Backpack to other inventories.", NamedTextColor.RED));
                return;
            }
            
            return;
        }

        
        if (clickedInv != null && clickedInv != player.getInventory()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't move the Team Backpack into containers.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        for (ItemStack item : event.getNewItems().values()) {
            if (isTeamBundle(item)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can't move the Team Backpack.", NamedTextColor.RED));
                break;
            }
        }
    }

    
    private void ensurePlayerHasBundle(Player player) {
        if (!globallyEnabled) return;
        Team team = getPlayerTeam(player);
        if (team != null && disabledTeams.contains(team.getName())) return;
        if (team != null && !hasTeamBundle(player)) {
            ItemStack bundle = createTeamBundle();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(bundle);
            if (!leftover.isEmpty()) { 
                ItemStack existing = player.getInventory().getItem(0);
                if (existing != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), existing); 
                }
                player.getInventory().setItem(0, bundle);
            }
        }
    }

    
    private Team getPlayerTeam(Player player) {
        
        player.getScoreboard();
        Team t = player.getScoreboard().getEntryTeam(player.getName());
        if (t != null) return t;
        
        Bukkit.getScoreboardManager();
        return Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
    }

    
    private ItemStack createTeamBundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bundle.getItemMeta();
        meta.displayName(BUNDLE_NAME.decoration(TextDecoration.ITALIC, false));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(bundleKey, PersistentDataType.BYTE, (byte)1);
        meta.lore(Collections.singletonList(
            Component.text("Shared team storage", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        bundle.setItemMeta(meta);
        return bundle;
    }

    
    private boolean hasTeamBundle(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTeamBundle(item)) return true;
        }
        return false;
    }

    
    private boolean isTeamBundle(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        Component displayName = meta.displayName();
        if (displayName != null && !BUNDLE_NAME.equals(displayName)) {
            
            if (!BUNDLE_NAME.decoration(TextDecoration.ITALIC, false).equals(displayName)) {
                return false;
            }
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(bundleKey, PersistentDataType.BYTE);
    }

    
    public static class PublicChatCommand implements CommandExecutor {
        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
            if (!(sender instanceof Player player)) return false;
            if (args.length == 0) return false;
            String msg = String.join(" ", args);
            Component chatMsg = Component.text("[Public] ", NamedTextColor.YELLOW)
                    .append(player.displayName())
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(Component.text(msg, NamedTextColor.WHITE));
            Bukkit.broadcast(chatMsg);
            return true;
        }
    }

    
    public class BundleGiveCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
            if (!sender.hasPermission(PERM_BGIVE)) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(Component.text("Usage: /bgive <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found or not online.", NamedTextColor.RED));
                return true;
            }
            Team team = getPlayerTeam(target);
            if (team == null) {
                sender.sendMessage(Component.text("That player is not in a team.", NamedTextColor.RED));
                return true;
            }
            if (hasTeamBundle(target)) {
                sender.sendMessage(Component.text("That player already has a Team Backpack.", NamedTextColor.YELLOW));
                return true;
            }
            ItemStack bundle = createTeamBundle();
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(bundle);
            if (!leftover.isEmpty()) {
                target.getWorld().dropItemNaturally(target.getLocation(), bundle);
            }
            sender.sendMessage(Component.text("Gave a Team Backpack to " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("You were given a Team Backpack.", NamedTextColor.GOLD));
            return true;
        }
    }

    
    public class ToggleCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
            if (!sender.hasPermission(PERM_TOGGLE)) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(Component.text("Usage: /teamutils <enable|disable> [global|team <teamname>]", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Examples:", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("  /teamutils disable global - Disable for all teams", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /teamutils enable global - Enable for all teams", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /teamutils disable team Red - Disable for Red team", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /teamutils enable team Blue - Enable for Blue team", NamedTextColor.GRAY));
                return true;
            }

            String action = args[0].toLowerCase();
            if (!action.equals("enable") && !action.equals("disable")) {
                sender.sendMessage(Component.text("Invalid action. Use 'enable' or 'disable'.", NamedTextColor.RED));
                return true;
            }

            boolean enable = action.equals("enable");

            
            if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("global"))) {
                
                globallyEnabled = enable;
                if (enable) {
                    sender.sendMessage(Component.text("TeamUtils enabled globally.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("TeamUtils disabled globally.", NamedTextColor.YELLOW));
                }
                return true;
            }

            
            if (args[1].equalsIgnoreCase("team")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /teamutils " + action + " team <teamname>", NamedTextColor.RED));
                    return true;
                }

                String teamName = args[2];
                Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
                if (team == null) {
                    sender.sendMessage(Component.text("Team '" + teamName + "' not found.", NamedTextColor.RED));
                    return true;
                }

                if (enable) {
                    disabledTeams.remove(team.getName());
                    sender.sendMessage(Component.text("TeamUtils enabled for team '" + team.getName() + "'.", NamedTextColor.GREEN));
                } else {
                    disabledTeams.add(team.getName());
                    sender.sendMessage(Component.text("TeamUtils disabled for team '" + team.getName() + "'.", NamedTextColor.YELLOW));
                }
                return true;
            }

            sender.sendMessage(Component.text("Invalid usage. Use /teamutils for help.", NamedTextColor.RED));
            return true;
        }
    }
}

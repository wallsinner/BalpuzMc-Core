package com.balpuzmc.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class BalpuzCore extends JavaPlugin implements Listener {

    private static BalpuzCore instance;
    private final Map<UUID, UserData> playerData = new HashMap<>();
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<String, LocationData> warps = new HashMap<>();
    private final List<OrderData> orders = new ArrayList<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public static final String PREFIX = "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8» §7";

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        loadCustomConfig();
        
        startClearLagTask();
        startScoreboardTask();
        startKeyTimerTask();

        getLogger().info("BalpuzMc-Core 1.21.4 (Java 21) aktif edildi!");
    }

    @Override
    public void onDisable() {
        saveCustomConfig();
    }

    public static BalpuzCore getInstance() { return instance; }

    private void loadCustomConfig() {
        dataFile = new File(getDataFolder(), "userdata.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("userdata.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveCustomConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        String cmd = command.getName().toLowerCase();
        
        if (cmd.equals("rtp")) {
            openRtpMenu(player);
        } else if (cmd.equals("pazar")) {
            openMarketMenu(player);
        } else if (cmd.equals("siparis")) {
            openOrderMenu(player, args);
        } else if (cmd.equals("sat")) {
            openSellMenu(player);
        } else if (cmd.equals("invsee")) {
            if (!player.isOp()) { player.sendMessage(PREFIX + "Bu komut için yetkin yok!"); return true; }
            if (args.length < 1) { player.sendMessage(PREFIX + "/invsee <oyuncu>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) player.openInventory(target.getInventory());
        } else if (cmd.equals("tpa")) {
            handleTpa(player, args, false);
        } else if (cmd.equals("tphere")) {
            handleTpa(player, args, true);
        } else if (cmd.equals("tpaccept")) {
            acceptTpa(player);
        } else if (cmd.equals("tpdeny")) {
            denyTpa(player);
        } else if (cmd.equals("para") || cmd.equals("shard")) {
            handleEconomy(player, cmd, args);
        } else if (cmd.equals("ametist")) {
            if (!player.isOp()) return true;
            giveAmetystTools(player);
        } else if (cmd.equals("warp")) {
            handleWarp(player, args);
        }
        return true;
    }

    private void openRtpMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8- RTP");
        
        ItemStack nether = new ItemStack(Material.NETHERRACK);
        ItemMeta nm = nether.getItemMeta();
        nm.setDisplayName("§cNether RTP (1k / -1k)");
        nether.setItemMeta(nm);

        ItemStack world = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta wm = world.getItemMeta();
        wm.setDisplayName("§aDünya RTP (2k / -2k)");
        world.setItemMeta(wm);

        ItemStack end = new ItemStack(Material.END_STONE);
        ItemMeta em = end.getItemMeta();
        em.setDisplayName("§dEnd RTP (2k / -2k)");
        end.setItemMeta(em);

        inv.setItem(2, nether);
        inv.setItem(4, world);
        inv.setItem(6, end);
        player.openInventory(inv);
    }

    private void startClearLagTask() {
        new BukkitRunnable() {
            int timer = 600;
            @Override
            public void run() {
                timer--;
                if (timer == 300) Bukkit.broadcastMessage(PREFIX + "§eYerdeki eşyalar 5 dakika sonra temizlenecek!");
                if (timer == 180) Bukkit.broadcastMessage(PREFIX + "§eYerdeki eşyalar 3 dakika sonra temizlenecek!");
                if (timer == 60) Bukkit.broadcastMessage(PREFIX + "§eYerdeki eşyalar 1 dakika sonra temizlenecek!");
                if (timer == 30) Bukkit.broadcastMessage(PREFIX + "§eYerdeki eşyalar 30 saniye sonra temizlenecek!");
                if (timer <= 5 && timer > 0) Bukkit.broadcastMessage(PREFIX + "§cTemizlik: " + timer);
                if (timer <= 0) {
                    int count = 0;
                    for (org.bukkit.World w : Bukkit.getWorlds()) {
                        for (org.bukkit.entity.Entity e : w.getEntities()) {
                            if (e instanceof org.bukkit.entity.Item) {
                                e.remove();
                                count++;
                            }
                        }
                    }
                    Bukkit.broadcastMessage(PREFIX + "§a§lʙᴀʟᴘᴜᴢᴍᴄ Temizlikçi: §7Yerdeki " + count + " eşya temizlendi!");
                    timer = 600;
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void openMarketMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8- Pazar");
        player.openInventory(inv);
    }

    private void openSellMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8- Satış");
        player.openInventory(inv);
    }

    private void openOrderMenu(Player player, String[] args) {
        player.sendMessage(PREFIX + "Sipariş sistemi aktif. /sipariş oluştur <kod> <miktar> <fiyat>");
    }

    private void startScoreboardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter("§9§lʙᴀʟᴘᴜᴢᴍᴄ\n§7Aktif Oyuncu: " + Bukkit.getOnlinePlayers().size(),
                            "\n§f/discord §8| §f/bilgi §8| §f/site");
                }
            }
        }.runTaskTimer(this, 0L, 40L);
    }

    private void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("sb", Criteria.DUMMY, "§9§lʙᴀʟᴘᴜᴢᴍᴄ");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        UserData data = getOrCreateData(player.getUniqueId());

        obj.getScore("§8----------------").setScore(8);
        obj.getScore("§7Para: §a$" + data.money).setScore(7);
        obj.getScore("§7Shard: §b✦ " + data.shard).setScore(6);
        obj.getScore("§7Ölme: §c" + data.deaths).setScore(5);
        obj.getScore("§7Öldürme: §e" + data.kills).setScore(4);
        obj.getScore("§7Kasa Anahtarı: §6" + data.crateTimer + "dk").setScore(3);
        obj.getScore("§7Oynama: §f" + (player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 72000) + "s").setScore(2);
        obj.getScore("§7Ping: §a" + player.getPing() + "ms").setScore(1);

        player.setScoreboard(board);
    }

    private void handleTpa(Player player, String[] args, boolean isHere) {
        if (args.length < 1) { player.sendMessage(PREFIX + "Kullanım: /tpa <oyuncu>"); return; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { player.sendMessage(PREFIX + "Oyuncu bulunamadı!"); return; }
        
        tpaRequests.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(PREFIX + "Işınlanma isteği gönderildi.");
        target.sendMessage(PREFIX + player.getName() + " sana bir ışınlanma isteği attı! Kabul etmek için /tpaccept");
    }

    private void acceptTpa(Player player) {
        UUID senderUUID = tpaRequests.get(player.getUniqueId());
        if (senderUUID == null) { player.sendMessage(PREFIX + "Aktif isteğin yok."); return; }
        Player sender = Bukkit.getPlayer(senderUUID);
        if (sender != null) {
            sender.teleport(player.getLocation());
            sender.sendMessage(PREFIX + "Işınlanma kabul edildi!");
        }
        tpaRequests.remove(player.getUniqueId());
    }

    private void denyTpa(Player player) {
        tpaRequests.remove(player.getUniqueId());
        player.sendMessage(PREFIX + "İstek reddedildi.");
    }

    private void giveAmetystTools(Player player) {
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.setDisplayName("§d✦ Ametist Kazma");
        pick.setItemMeta(meta);
        player.getInventory().addItem(pick);
        player.sendMessage(PREFIX + "Ametist aletler verildi.");
    }

    private void handleEconomy(Player player, String cmd, String[] args) {
        if (args.length < 2) return;
        Player target = Bukkit.getPlayer(args[0]);
        double amount = Double.parseDouble(args[1]);
        UserData data = getOrCreateData(player.getUniqueId());

        if (cmd.equals("para")) {
            data.money += amount;
            player.sendMessage(PREFIX + "İşlem başarılı.");
        } else {
            data.shard += (int) amount;
            player.sendMessage(PREFIX + "Shard güncellendi.");
        }
    }

    private void handleWarp(Player player, String[] args) {
        if (args.length < 1) return;
        if (args[0].equalsIgnoreCase("set") && player.isOP()) {
            warps.put(args[1], new LocationData(player.getLocation()));
            player.sendMessage(PREFIX + "Warp oluşturuldu.");
        } else {
            LocationData loc = warps.get(args[0]);
            if (loc != null) player.teleport(loc.toLocation());
        }
    }

    private UserData getOrCreateData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> new UserData());
    }

    private void startKeyTimerTask() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("ʙᴀʟᴘᴜᴢᴍᴄ")) {
            event.setCancelled(true);
        }
    }

    private static class UserData {
        double money = 100.0;
        int shard = 10;
        int deaths = 0;
        int kills = 0;
        int crateTimer = 60;
    }

    private static class LocationData {
        String world; double x, y, z;
        LocationData(org.bukkit.Location l) {
            this.world = l.getWorld().getName();
            this.x = l.getX(); this.y = l.getY(); this.z = l.getZ();
        }
        org.bukkit.Location toLocation() {
            return new org.bukkit.Location(Bukkit.getWorld(world), x, y, z);
        }
    }

    private static class OrderData {}
}

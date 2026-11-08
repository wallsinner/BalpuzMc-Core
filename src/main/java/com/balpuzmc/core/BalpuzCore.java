package com.balpuzmc.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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
    private final List<MarketItem> marketItems = new ArrayList<>();
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

        getLogger().info("BalpuzMc-Core 1.21.4 (Java 21) aktif edildi!");
    }

    @Override
    public void onDisable() {
        saveCustomConfig();
    }

    public static BalpuzCore getInstance() { return instance; }

    private void loadCustomConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        dataFile = new File(getDataFolder(), "userdata.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveCustomConfig() {
        try {
            if (dataConfig != null && dataFile != null) {
                dataConfig.save(dataFile);
            }
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
        } else if (cmd.equals("rtpq")) {
            handleRtpQ(player, args);
        } else if (cmd.equals("pazar")) {
            handleMarketCommand(player, args);
        } else if (cmd.equals("siparis")) {
            handleOrderCommand(player, args);
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

    private void executeRtp(Player player, String type) {
        Random rand = new Random();
        World targetWorld;
        int bound;

        if (type.equals("nether")) {
            targetWorld = Bukkit.getWorld("world_nether");
            if (targetWorld == null) targetWorld = Bukkit.getWorlds().get(0);
            bound = 1000;
        } else if (type.equals("end")) {
            targetWorld = Bukkit.getWorld("world_the_end");
            if (targetWorld == null) targetWorld = Bukkit.getWorlds().get(0);
            bound = 2000;
        } else {
            targetWorld = Bukkit.getWorlds().get(0);
            bound = 2000;
        }

        int x = rand.nextInt(bound * 2) - bound;
        int z = rand.nextInt(bound * 2) - bound;
        int y = targetWorld.getHighestBlockYAt(x, z) + 1;

        Location loc = new Location(targetWorld, x + 0.5, y, z + 0.5);
        
        Material ground = loc.clone().subtract(0, 1, 0).getBlock().getType();
        if (ground == Material.LAVA || ground == Material.WATER || ground == Material.AIR) {
            player.sendMessage(PREFIX + "Güvenli yer bulunamadı, tekrar deneniyor...");
            executeRtp(player, type);
            return;
        }

        player.teleport(loc);
        player.sendMessage(PREFIX + "Rastgele bölgeye ışınlandın!");
    }

    private void handleRtpQ(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(PREFIX + "Kullanım: /rtpq <oyuncu>"); return; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { player.sendMessage(PREFIX + "Oyuncu bulunamadı!"); return; }
        
        Random rand = new Random();
        World w = player.getWorld();
        int x = rand.nextInt(4000) - 2000;
        int z = rand.nextInt(4000) - 2000;
        int y = w.getHighestBlockYAt(x, z) + 1;

        Location loc = new Location(w, x + 0.5, y, z + 0.5);
        player.teleport(loc);
        target.teleport(loc);
        player.sendMessage(PREFIX + target.getName() + " ile birlikte rastgele bir yere ışınlandın!");
        target.sendMessage(PREFIX + player.getName() + " ile birlikte ışınlandın!");
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
                    for (World w : Bukkit.getWorlds()) {
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

    private void handleMarketCommand(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("sat")) {
            if (args.length < 2) { player.sendMessage(PREFIX + "Kullanım: /pazar sat <fiyat>"); return; }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) { player.sendMessage(PREFIX + "Elinde satılacak bir eşya yok!"); return; }
            
            double price;
            try { price = Double.parseDouble(args[1]); } catch (Exception e) { player.sendMessage(PREFIX + "Geçersiz fiyat!"); return; }

            marketItems.add(new MarketItem(player.getName(), item.clone(), price));
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.sendMessage(PREFIX + "Eşya pazara başarıyla eklendi!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8- Pazar");
        int slot = 0;
        for (MarketItem mi : marketItems) {
            if (slot >= 27) break;
            ItemStack display = mi.item.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("§8----------------");
            lore.add("§7Satıcı: §f" + mi.seller);
            lore.add("§7Fiyat: §a$" + mi.price);
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot++, display);
        }
        player.openInventory(inv);
    }

    private void handleOrderCommand(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("oluştur")) {
            if (args.length < 4) { player.sendMessage(PREFIX + "Kullanım: /sipariş oluştur <eşya> <miktar> <fiyat>"); return; }
            Material mat = Material.matchMaterial(args[1].toUpperCase());
            if (mat == null) { player.sendMessage(PREFIX + "Geçersiz eşya kodu!"); return; }
            int amount = Integer.parseInt(args[2]);
            double price = Double.parseDouble(args[3]);

            orders.add(new OrderData(player.getName(), mat, amount, price));
            player.sendMessage(PREFIX + "Sipariş başarıyla oluşturuldu!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8- Siparişler");
        int slot = 0;
        for (OrderData od : orders) {
            if (slot >= 27) break;
            ItemStack item = new ItemStack(od.material, od.amount);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§eSipariş: " + od.material.name());
            meta.setLore(List.of("§7Veren: " + od.buyer, "§7Ödül: $" + od.price));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
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

    private void openSellMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§9§lʙᴀʟᴘᴜᴢᴍᴄ §8- Satış Menüsü");
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta cm = confirm.getItemMeta();
        cm.setDisplayName("§a§lTümünü Sat");
        confirm.setItemMeta(cm);
        inv.setItem(53, confirm);
        player.openInventory(inv);
    }

    private void giveAmetystTools(Player player) {
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.setDisplayName("§d✦ Ametist Kazma");
        pick.setItemMeta(meta);

        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta axem = axe.getItemMeta();
        axem.setDisplayName("§d✦ Ametist Balta");
        axe.setItemMeta(axem);

        player.getInventory().addItem(pick, axe);
        player.sendMessage(PREFIX + "Ametist aletler verildi.");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("Ametist Kazma")) {
            Location loc = event.getBlock().getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType() != Material.BEDROCK) b.breakNaturally();
                    }
                }
            }
        } else if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("Ametist Balta")) {
            Block block = event.getBlock();
            if (block.getType().name().endsWith("_LOG") || block.getType().name().endsWith("_WOOD")) {
                block.breakNaturally();
            } else if (block.getState() instanceof Chest chest) {
                for (ItemStack i : chest.getInventory().getContents()) {
                    if (i != null && i.getType() != Material.AIR) {
                        UserData data = getOrCreateData(player.getUniqueId());
                        data.money += getSellPrice(i.getType()) * i.getAmount();
                    }
                }
                chest.getInventory().clear();
                player.sendMessage(PREFIX + "Sandıktaki tüm eşyalar /sat ile dönüştürüldü!");
            }
        }
    }

    private double getSellPrice(Material mat) {
        if (mat == Material.NETHERITE_INGOT) return 5000000.0;
        if (mat == Material.DIAMOND) return 10000.0;
        return 100.0;
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
        if (args[0].equalsIgnoreCase("set") && player.isOp()) {
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("ʙᴀʟᴘᴜᴢᴍᴄ §8- RTP")) {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            if (event.getSlot() == 2) executeRtp(p, "nether");
            if (event.getSlot() == 4) executeRtp(p, "world");
            if (event.getSlot() == 6) executeRtp(p, "end");
            p.closeInventory();
        } else if (title.contains("Satış Menüsü")) {
            if (event.getRawSlot() == 53) {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                double totalEarned = 0;
                for (int i = 0; i < 53; i++) {
                    ItemStack item = event.getInventory().getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        totalEarned += getSellPrice(item.getType()) * item.getAmount();
                        event.getInventory().setItem(i, null);
                    }
                }
                UserData data = getOrCreateData(p.getUniqueId());
                data.money += totalEarned;
                p.sendMessage(PREFIX + "Eşyalar satıldı! Kazanılan: $" + totalEarned);
            }
        } else if (title.contains("ʙᴀʟᴘᴜᴢᴍᴄ")) {
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
        LocationData(Location l) {
            this.world = l.getWorld().getName();
            this.x = l.getX(); this.y = l.getY(); this.z = l.getZ();
        }
        Location toLocation() {
            return new Location(Bukkit.getWorld(world), x, y, z);
        }
    }

    private static class MarketItem {
        String seller; ItemStack item; double price;
        MarketItem(String s, ItemStack i, double p) { this.seller = s; this.item = i; this.price = p; }
    }

    private static class OrderData {
        String buyer; Material material; int amount; double price;
        OrderData(String b, Material m, int a, double p) { this.buyer = b; this.material = m; this.amount = a; this.price = p; }
    }
}

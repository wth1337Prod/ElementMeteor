package ru.elementcraft.elementmeteor;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.Element;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.elementcraft.elementmeteor.ability.MeteorAbility;
import ru.elementcraft.elementmeteor.command.ElementMeteorCommand;
import ru.elementcraft.elementmeteor.config.LocaleManager;
import ru.elementcraft.elementmeteor.database.DatabaseManager;
import ru.elementcraft.elementmeteor.util.CacheManager;
import ru.elementcraft.elementmeteor.util.SecurityManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ElementMeteor extends JavaPlugin implements Listener {

    private static final int CONFIG_VERSION = 1;
    private static ElementMeteor instance;

    private DatabaseManager databaseManager;
    private LocaleManager localeManager;
    private CacheManager cacheManager;
    private SecurityManager securityManager;
    private LiteCommands<CommandSender> liteCommands;

    private final AtomicInteger meteorLaunchCount = new AtomicInteger(0);
    private final Set<UUID> recentDropAttempts = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;

        this.securityManager = new SecurityManager(this);
        saveDefaultConfig();
        validateConfig();
        this.localeManager = new LocaleManager(this);
        this.databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        this.cacheManager = new CacheManager(this, databaseManager);
        this.registerCommands();

        // Регистрация способности в Проджекткорра
        CoreAbility.registerPluginAbilities(this, MeteorAbility.class.getPackage().getName());

        getServer().getPluginManager().registerEvents(this, this);
        setupMetrics();
    }

    private void registerCommands() {
        try {
            this.liteCommands = LiteBukkitFactory.builder()
                    .commands(new ElementMeteorCommand())
                    .build();

            getLogger().info("LiteCommands 3.9.6 успешно зарегистрированы!");
        } catch (Exception e) {
            getLogger().severe("Ошибка при регистрации команд: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void validateConfig() {
        int configVersion = getConfig().getInt("config-version", 0);
        boolean needsSave = false;

        if (configVersion < CONFIG_VERSION) {
            needsSave = true;
            getConfig().set("config-version", CONFIG_VERSION);
        }

        if (!getConfig().isConfigurationSection("database")) {
            getConfig().createSection("database");
            getConfig().set("database.host", "localhost");
            getConfig().set("database.port", 3306);
            getConfig().set("database.name", "elementmeteor");
            getConfig().set("database.username", "root");
            getConfig().set("database.password", securityManager.encryptDefaultPassword());
            needsSave = true;
        }

        if (!getConfig().isConfigurationSection("ability")) {
            getConfig().createSection("ability");
            getConfig().set("ability.cooldown", 5000);
            getConfig().set("ability.range", 100.0);
            getConfig().set("ability.speed", 1.2);
            getConfig().set("ability.damage", 5.0);
            getConfig().set("ability.temp-block-duration", 5000);
            getConfig().set("ability.base-radius", 3.0);
            getConfig().set("ability.max-additional-radius", 3.0);
            needsSave = true;
        }

        if (!getConfig().isConfigurationSection("settings")) {
            getConfig().createSection("settings");
            getConfig().set("settings.locale", "ru_RU");
            needsSave = true;
        }

        if (!getConfig().isConfigurationSection("messages")) {
            getConfig().createSection("messages");
            needsSave = true;
        }

        String[][] requiredMessages = {
            {"prefix", "&6[ElementMeteor] &r"},
            {"no-bending-player", "&cОшибка: Игрок не найден в системе"},
            {"ability-given", "&6Вы получили способность &cМетеор&6!"},
            {"ability-used", "&6Вы запустили метеор!"},
            {"ability-item-name", "Метеорная способность"},
            {"ability-item-description", "ЛКМ - запустить метеор"},
            {"command-player-only", "&cЭту команду могут использовать только игроки!"},
            {"command-no-permission", "&cУ вас нет прав на использование этой команды!"},
            {"menu-error", "&cПроизошла ошибка при открытии меню!"},
            {"wrong-element", "&cВы не можете взять метеор пока у вас установлен другой элемент! Установите элемент &6Fire &cчерез &e/bending choose Fire"},
            {"no-element", "&cУ вас не выбран элемент! Выберите элемент &6Fire &cчерез &e/bending choose Fire"},
            {"ability-already-exists", "&cУ вас уже есть способность &6Метеор &cв инвентаре!"}
        };

        for (String[] message : requiredMessages) {
            String key = "messages." + message[0];
            String defaultValue = message[1];

            if (!getConfig().isSet(key)) {
                getConfig().set(key, defaultValue);
                needsSave = true;
            }
        }

        if (needsSave) {
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            try {
                databaseManager.shutdown();
            } catch (Exception e) {
            }
        }

        if (liteCommands != null) {
            try {
                liteCommands.unregister();
            } catch (Exception e) {
            }
        }

        if (cacheManager != null) {
            cacheManager.clearCache();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Строго проверяем что это левый клик
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!isMeteorAbilityItem(heldItem)) {
            return;
        }

        // Отменяем событие, чтобы избежать нежелательных взаимодействий
        event.setCancelled(true);
        
        // Проверяем, не пытался ли игрок недавно выбросить предмет
        if (recentDropAttempts.contains(player.getUniqueId())) {
            return; // Игнорируем это взаимодействие, оно вызвано попыткой выбросить предмет
        }

        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) {
            player.sendMessage(localeManager.getMessage("no-bending-player"));
            return;
        }

        // Запуск метеора - главная логика
        new MeteorAbility(player, this);

        meteorLaunchCount.incrementAndGet();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        
        // Проверяем, является ли выброшенный предмет метеорной способностью
        if (isMeteorAbilityItem(droppedItem)) {
            // ЗАПРЕЩАЕМ выбрасывать способность
            event.setCancelled(true);
            
            Player player = event.getPlayer();
            player.sendActionBar("§c✖ Нельзя выбрасывать способности!");
            
            // Добавляем игрока в список недавних попыток выбросить
            UUID playerId = player.getUniqueId();
            recentDropAttempts.add(playerId);
            
            // Удаляем из списка через 500 мс
            Bukkit.getScheduler().runTaskLater(this, () -> recentDropAttempts.remove(playerId), 10L);
            
            // Играем звук ошибки
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Удаляем все предметы способностей из дропа при смерти
        event.getDrops().removeIf(this::isMeteorAbilityItem);
    }

    // Обработка правого клика для предотвращения неправильной активации
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!isMeteorAbilityItem(heldItem)) {
            return;
        }

        // Отменяем событие правого клика для метеора
        event.setCancelled(true);
        
    }

    private boolean isMeteorAbilityItem(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_POWDER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(this, "meteor_ability");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public ItemStack createMeteorAbilityItem() {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();

        ChatColor lagoonColor = ChatColor.AQUA;
        ChatColor grayColor = ChatColor.GRAY;

        if (meta != null) {
            meta.setDisplayName(lagoonColor + localeManager.getMessage("ability-item-name"));

            List<String> lore = new ArrayList<>();
            lore.add(lagoonColor + "‣ " + grayColor + localeManager.getMessage("ability-item-description"));

            double baseRadius = getConfig().getDouble("ability.base-radius", 3.0);
            lore.add(lagoonColor + "‣ Базовый радиус: " + grayColor + String.format("%.1f", baseRadius));

            meta.setLore(lore);

            // Пометка предмета как способность
            NamespacedKey key = new NamespacedKey(this, "meteor_ability");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "true");

            item.setItemMeta(meta);
        }

        return item;
    }

    private void setupMetrics() {
    }

    public int getPlayerUsageCount(UUID playerUuid) {
        return cacheManager.getUsageCount(playerUuid);
    }

    public void incrementPlayerUsageCount(UUID playerUuid) {
        cacheManager.incrementUsageCount(playerUuid);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public static ElementMeteor getInstance() {
        return instance;
    }
}

package ru.elementcraft.elementmeteor.util;

import ru.elementcraft.elementmeteor.ElementMeteor;
import ru.elementcraft.elementmeteor.database.DatabaseManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер кэширования для данных плагина
 */
public class CacheManager {
    private final ElementMeteor plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Integer> usageCountCache = new HashMap<>();
    private final Map<UUID, Boolean> dirtyCache = new HashMap<>();
    private final ScheduledExecutorService scheduler;

    public CacheManager(ElementMeteor plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(this::flushDirtyCache, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Получает количество использований способности игроком
     * @param playerUuid UUID игрока
     * @return Количество использований
     */
    public int getUsageCount(UUID playerUuid) {
        if (usageCountCache.containsKey(playerUuid)) {
            return usageCountCache.get(playerUuid);
        }

        CompletableFuture<Integer> future = databaseManager.getPlayerUsageCountAsync(playerUuid);

        try {
            int count = future.get();
            usageCountCache.put(playerUuid, count);
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Увеличивает счетчик использований способности игроком
     * @param playerUuid UUID игрока
     */
    public void incrementUsageCount(UUID playerUuid) {
        int currentCount = getUsageCount(playerUuid);
        usageCountCache.put(playerUuid, currentCount + 1);
        dirtyCache.put(playerUuid, true);

        CompletableFuture.runAsync(() -> {
            try {
                databaseManager.updatePlayerUsageCountAsync(playerUuid, currentCount + 1);
                dirtyCache.remove(playerUuid);
            } catch (Exception e) {
            }
        });
    }

    public void setUsageCount(UUID playerUuid, int count) {
        usageCountCache.put(playerUuid, count);
    }

    /**
     * Очищает весь кэш
     */
    public void clearCache() {
        flushDirtyCache();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        usageCountCache.clear();
        dirtyCache.clear();
    }

    private void flushDirtyCache() {
        for (UUID playerUuid : new HashMap<>(dirtyCache).keySet()) {
            if (dirtyCache.get(playerUuid) && usageCountCache.containsKey(playerUuid)) {
                int count = usageCountCache.get(playerUuid);

                try {
                    databaseManager.updatePlayerUsageCountAsync(playerUuid, count);
                    dirtyCache.remove(playerUuid);
                } catch (Exception e) {
                }
            }
        }
    }
}

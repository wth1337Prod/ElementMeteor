package ru.elementcraft.elementmeteor.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.elementcraft.elementmeteor.ElementMeteor;

import java.io.File;

/**
 * менеджер локализации для сообщений плагина
 */
public class LocaleManager {
    private final ElementMeteor plugin;
    private YamlConfiguration messages;
    private final String locale;

    public LocaleManager(ElementMeteor plugin) {
        this.plugin = plugin;
        this.locale = plugin.getConfig().getString("settings.locale", "ru_RU");

        loadMessages();
    }

    /**
     * загружает сообщения из файла локализации или конфига
     */
    private void loadMessages() {
        messages = new YamlConfiguration();
        
        // Сначала загружаем все сообщения из конфига
        ConfigurationSection messagesSection = plugin.getConfig().getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.set(key, messagesSection.getString(key));
            }
        }
        
        // Затем пытаемся загрузить файл локализации
        File localeFile = new File(plugin.getDataFolder(), "locale_" + locale + ".yml");

        if (!localeFile.exists()) {
            try {
                plugin.saveResource("locale_" + locale + ".yml", false);
            } catch (Exception e) {
                // Игнорируем ошибку
            }
        }

        if (localeFile.exists()) {
            try {
                YamlConfiguration localeMessages = YamlConfiguration.loadConfiguration(localeFile);
                // Перезаписываем сообщения из файла локализации
                for (String key : localeMessages.getKeys(false)) {
                    messages.set(key, localeMessages.getString(key));
                }
            } catch (Exception e) {
                // Игнорируем ошибку и продолжаем с сообщениями из конфига
            }
        }
    }

    /**
     * Получает сообщение из файла локализации
     * @param key Ключ сообщения
     * @return Сообщение с примененными цветовыми кодами
     */
    public String getMessage(String key) {
        return getMessage(key, "Message not found: " + key);
    }

    /**
     * Получает сообщение из файла локализации с поддержкой формата
     * @param key Ключ сообщения
     * @param defaultValue Значение по умолчанию
     * @param args Аргументы для форматирования
     * @return Сообщение с примененными цветовыми кодами и аргументами
     */
    public String getMessage(String key, String defaultValue, Object... args) {
        String message = messages.getString(key, defaultValue);

        if (args.length > 0) {
            try {
                message = String.format(message, args);
            } catch (Exception e) {
            }
        }

        return colorize(message);
    }

    /**
     * Заменяет символы & на коды цветов
     * @param message Сообщение
     * @return Сообщение с примененными кодами цветов
     */
    private String colorize(String message) {
        return message.replace('&', '§');
    }

    /**
     * получает текущую локаль
     * @return Код локали
     */
    public String getLocale() {
        return locale;
    }
}

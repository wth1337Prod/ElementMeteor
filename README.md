# 🌟 ElementMeteor Plugin

[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://java.com)
[![Paper](https://img.shields.io/badge/Paper-1.16.5-blue.svg)](https://papermc.io)
[![LiteCommands](https://img.shields.io/badge/LiteCommands-3.9.6-green.svg)](https://github.com/Rollczi/LiteCommands)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> 🎯 **Тестовое задание для ElementCraft** - Плагин для майнкрафта с системой метеора

## 📋 Описание

ElementMeteor - это плагин для Paper 1.16.5, который добавляет способность **Метеор**.

### ⚡ Основные возможности

- **Команда `/elementmeteor`** - открывает GUI меню
- **Способность Метеор** - создает разрушительный метеор
- **Система прогрессии** - радиус взрыва увеличивается с опытом
- **База данных** - по заданию ТЗ
- **Локализация** - поддержка русского языка и английского языка
- **Анимированное восстановление** - блоки восстанавливаются с визуальными эффектами через 4 секунды
- **Защита от выбрасывания** - предотвращение потери предмета способности

## 🛠 Технический стек

| Технология | Версия | Назначение |
|------------|--------|------------|
| **Java** | 8+ | Основной язык |
| **Gradle** | Kotlin DSL | Система сборки |
| **LiteCommands** | 3.9.6 | Обработка команд |
| **plugin-yml** | 0.6.0 | Генерация plugin.yml |
| **HikariCP** | 4.0.3 | Пул соединений БД |
| **MariaDB** | 3.0.7 | Драйвер базы данных |
| **Paper** | 1.16.5 | Серверная платформа |
| **ProjectKorra** | 1.11.2 | API способностей |
| **Triumph-GUI** | 3.1.2 | Интерфейсы |

## 🚀 Быстрый старт

### Требования
- **Java 8+**
- **Paper 1.16.5**
- **ProjectKorra** (последняя версия для 1.16.5)
- **MariaDB Сервер**

### Установка

1. **Скачайте JAR** из [Releases](../../releases)
2. **Поместите** в папку `plugins/`
3. **Настройте БД** в `config.yml`
4. **Перезапустите** сервер

### Конфигурация базы данных

```yaml
database:
  host: "localhost"
  port: 3306
  database: "elementmeteor"
  username: "user"
  password: "password"
```

## 🎮 Использование

1. **Получите способность**: `/elementmeteor` → нажмите кнопку в меню
2. **Используйте метеор**: ЛКМ с предметом в руке
3. **Прогрессия**: Каждое использование увеличивает радиус взрыва

### Формула прогрессии
```
Радиус взрыва = (количество использований) × (базовый радиус)
```

## 🏗 Архитектура

```
src/main/java/ru/elementcraft/elementmeteor/
├── ElementMeteor.java         
├── commands/
│   └── ElementMeteorCommand.java    
├── gui/
│   └── ElementMeteorGUI.java      
├── abilities/
│   └── MeteorAbility.java         
├── database/
│   ├── DatabaseManager.java      
│   └── PlayerStatsDAO.java       
├── utils/
│   └── ConfigManager.java          
└── listeners/
    └── PlayerInteractListener.java 
```

## 📊 База данных

```sql
CREATE TABLE player_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    meteor_uses INT DEFAULT 0,
    last_used TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_damage DOUBLE DEFAULT 0.0,
    blocks_destroyed INT DEFAULT 0
);
```

## 🔧 Разработка

### Сборка проекта

```bash
# Компиляция
./gradlew build

# Создание JAR с зависимостями
./gradlew shadowJar
```


## 📈 Особенности реализации

### ⚡ Асинхронная работа с БД
- Все операции I/O выполняются асинхронно
- HikariCP для оптимального пулинга соединений
- Graceful shutdown с корректным закрытием соединений

### 🎨 Современная архитектура
- Разделение логики по слоям (MVC pattern)
- Dependency Injection через getInstance()
- Event-driven подход с Bukkit API

### 🔄 Интеграция с ProjectKorra
- Использование TempBlock API для временных блоков
- Корректная работа с системой способностей
- Соблюдение соглашений ProjectKorra

## 📝 Лицензия

Этот проект создан в рамках тестового задания для **ElementCraft**.

---

### 🎯 Соответствие требованиям

✅ **Java** (не Kotlin/Groovy)  
✅ **Gradle с Kotlin DSL**  
✅ **LiteCommands** для команд  
✅ **plugin-yml** для генерации  
✅ **HikariCP** для БД  
✅ **MariaDB Connector/J**  
✅ **Paper 1.16.5**  
✅ **ProjectKorra** интеграция  
✅ **GUI библиотека** (Triumph-GUI)  

**Дополнительно реализовано:**
- Система прогрессии с формулой
- Асинхронное I/O без блокировки главного потока
- Локализация сообщений
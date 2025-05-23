package ru.elementcraft.elementmeteor.command;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import ru.elementcraft.elementmeteor.ElementMeteor;

import java.util.List;

@Command(name = "elementmeteor", aliases = {"em"})
@Permission("elementmeteor.use")
public class ElementMeteorCommand {

    private ElementMeteor getPlugin() {
        return ElementMeteor.getInstance();
    }

    @Execute
    public void execute(@Context CommandSender sender) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getPlugin().getLocaleManager().getMessage("command-player-only"));
                return;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("elementmeteor.use")) {
                sender.sendMessage(getPlugin().getLocaleManager().getMessage("command-no-permission"));
                return;
            }

            openMenu(player);
        } catch (Exception e) {
            if (sender instanceof Player) {
                sender.sendMessage(getPlugin().getLocaleManager().getMessage("menu-error"));
            }
        }
    }

    private void openMenu(Player player) {
        Gui gui = Gui.gui()
                .title(Component.text("§8ElementMeteor Menu"))
                .rows(3)
                .disableAllInteractions()
                .create();

        GuiItem darkFiller = ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .flags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
                .asGuiItem(event -> event.setCancelled(true));

        for (int i = 0; i < gui.getInventory().getSize(); i++) {
            gui.setItem(i, darkFiller);
        }

        GuiItem grayFiller = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .flags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
                .asGuiItem(event -> event.setCancelled(true));

        int[] decorativeSlots = {3, 4, 5, 12, 14, 21, 22, 23};
        for (int slot : decorativeSlots) {
            gui.setItem(slot, grayFiller);
        }

        try {
            int usageCount = getPlugin().getPlayerUsageCount(player.getUniqueId());
            double baseRadius = getPlugin().getConfig().getDouble("ability.base-radius", 3.0);
            double currentRadius = usageCount * baseRadius;

            ItemBuilder builder = ItemBuilder.from(Material.NETHER_STAR)
                .name(Component.text("§b§lПолучить §f§lметеорную §b§lспособность"))
                .lore(
                    Component.text(""),
                    Component.text("§7▸ Нажмите, чтобы получить способность"),
                    Component.text("§7▸ Использований: §b" + usageCount),
                    Component.text("§7▸ Текущий радиус: §b" + String.format("%.1f", currentRadius) + " блоков"),
                    Component.text(""),
                    Component.text("§b§l[ЛКМ] §7Получить способность")
                )
                .flags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

            GuiItem meteorItem = builder.asGuiItem(event -> {
                event.setCancelled(true);

                BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
                
                if (bPlayer == null) {
                    player.sendMessage(getPlugin().getLocaleManager().getMessage("no-bending-player"));
                    gui.close(player);
                    return;
                }

                if (bPlayer.getElements().isEmpty()) {
                    player.sendMessage(getPlugin().getLocaleManager().getMessage("no-element"));
                    gui.close(player);
                    return;
                }

                if (hasAbilityItemInInventory(player)) {
                    player.sendMessage(getPlugin().getLocaleManager().getMessage("ability-already-exists", "У вас уже есть способность Метеор!", 0));
                    gui.close(player);
                    return;
                }

                try {
                    ItemStack abilityItem = getPlugin().createMeteorAbilityItem();
                    
                    int activeSlot = player.getInventory().getHeldItemSlot();
                    player.getInventory().setItem(activeSlot, abilityItem);
                    
                } catch (Exception e) {
                    player.sendMessage("§cОшибка при выдаче предмета способности");
                }

                gui.close(player);
            });

            gui.setItem(13, meteorItem);
        }
        catch (Exception e) {
            player.sendMessage("§cОшибка при создании меню");
        }

        gui.open(player);
    }

    private boolean hasAbilityItemInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.BLAZE_POWDER && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                NamespacedKey key = new NamespacedKey(getPlugin(), "meteor_ability");
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    return true;
                }
            }
        }
        return false;
    }
}

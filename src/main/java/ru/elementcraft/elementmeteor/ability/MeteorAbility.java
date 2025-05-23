package ru.elementcraft.elementmeteor.ability;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.ability.CoreAbility;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import ru.elementcraft.elementmeteor.ElementMeteor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MeteorAbility extends FireAbility implements AddonAbility {

    private final long cooldown;
    private final double range;
    private final double speed;
    private final double damage;
    private final double baseRadiusConfig;
    private final double maxRadiusConfig;
    private final int maxBlocksToCheckConfig;
    
    private final int restorationDelay;
    private final int waveSpeed;
    private final boolean restorationEffects;

    private Location location;
    private Vector direction;
    private double distanceTravelled;
    private boolean hasHit;
    private ArrayList<Block> affectedBlocks;
    private ElementMeteor plugin;
    private int playerUses;
    
    private Map<Block, Material> originalBlocks = new HashMap<>();

    public MeteorAbility(Player player, ElementMeteor plugin) {
        super(player);

        this.plugin = plugin;
        this.cooldown = plugin.getConfig().getLong("ability.cooldown", 5000);
        this.range = plugin.getConfig().getDouble("ability.range", 100);
        this.speed = plugin.getConfig().getDouble("ability.speed", 1.2);
        this.damage = plugin.getConfig().getDouble("ability.damage", 5.0);
        this.baseRadiusConfig = plugin.getConfig().getDouble("ability.base-radius", 3.0);
        this.maxRadiusConfig = plugin.getConfig().getDouble("ability.max-radius", 50.0);
        this.maxBlocksToCheckConfig = plugin.getConfig().getInt("ability.max-blocks-to-check", 5000);
        this.restorationDelay = plugin.getConfig().getInt("ability.restoration.delay", 4) * 20; // секунды -> тики
        this.waveSpeed = plugin.getConfig().getInt("ability.restoration.wave-speed", 3);
        this.restorationEffects = plugin.getConfig().getBoolean("ability.restoration.effects", true);

        if (!player.hasPermission("bending.ability.Meteor")) {
            player.sendMessage("§cУ вас нет прав на использование этой способности!");
            remove();
            return;
        }
        if (bPlayer.isChiBlocked()) {
            remove();
            return;
        }
        if (bPlayer.isOnCooldown(this.getName())) {
            long cooldownEndTime = bPlayer.getCooldown(this.getName());
            long currentTime = System.currentTimeMillis();
            long remainingTime = cooldownEndTime - currentTime;
            double remainingSeconds = remainingTime / 1000.0;
            
            if (remainingSeconds > 0) {
                player.sendActionBar("§c▸ Перезарядка: §f" + String.format("%.1f", remainingSeconds) + "с");
            }
            remove();
            return;
        }

        this.playerUses = plugin.getDatabaseManager().incrementPlayerUses(player);

        setFields();

        if (this.location == null || this.direction == null) {
            remove();
            return;
        }

        start();
        if (isRemoved()) {
            return;
        }
        bPlayer.addCooldown(this);
    }

    private void setFields() {
        this.location = player.getEyeLocation();
        this.direction = player.getEyeLocation().getDirection().normalize();
        this.distanceTravelled = 0;
        this.hasHit = false;
        this.affectedBlocks = new ArrayList<>();
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (hasHit) {
            remove();
            return;
        }

        if (distanceTravelled > range) {
            remove();
            return;
        }

        advanceMeteor();
    }

    private void advanceMeteor() {
        location = location.add(direction.clone().multiply(speed));
        distanceTravelled += speed;

        createMeteorEffect();

        Block block = location.getBlock();
        if (block.getType().isSolid() && !isRegionProtected(location)) {
            createExplosion();
            hasHit = true;
            return;
        }

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(location, 2.0)) {
            if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId()) {
                createExplosion();
                hasHit = true;
                return;
            }
        }
    }

    private boolean isRegionProtected(Location location) {
        return GeneralMethods.isRegionProtectedFromBuild(this.player, location);
    }

        private void createMeteorEffect() {
        ParticleEffect.FLAME.display(location, 30, 0.5, 0.5, 0.5, 0.2);
        ParticleEffect.SMOKE_LARGE.display(location, 15, 0.4, 0.4, 0.4, 0.1);
        ParticleEffect.LAVA.display(location, 5, 0.3, 0.3, 0.3, 0.05);

        ParticleEffect.EXPLOSION_LARGE.display(location, 1, 0.1, 0.1, 0.1, 0);

        player.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, location, 5, 0.2, 0.2, 0.2, 0.05);
        player.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, location, 10, 0.3, 0.3, 0.3, 0.1);

        for (int i = 0; i < 5; i++) {
            Location sparkLoc = location.clone().add(
                ThreadLocalRandom.current().nextDouble(-0.7, 0.7),
                ThreadLocalRandom.current().nextDouble(-0.7, 0.7),
                ThreadLocalRandom.current().nextDouble(-0.7, 0.7)
            );
            ParticleEffect.FIREWORKS_SPARK.display(sparkLoc, 3, 0.1, 0.1, 0.1, 0.05);
        }

        for (int i = 0; i < 8; i++) {
            Vector offset = new Vector(
                ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                ThreadLocalRandom.current().nextDouble(-1.0, 1.0)
            );

            Location trailLoc = location.clone().add(offset);
            ParticleEffect.FLAME.display(trailLoc, 5, 0.2, 0.2, 0.2, 0.05);
            ParticleEffect.SMOKE_NORMAL.display(trailLoc, 3, 0.1, 0.1, 0.1, 0.05);
        }

        player.getWorld().playSound(location, Sound.ENTITY_GHAST_SHOOT, 0.8f, 0.5f);
        player.getWorld().playSound(location, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.8f);
    }

    private void createExplosion() {
        double radius = playerUses * baseRadiusConfig;

        ParticleEffect.EXPLOSION_HUGE.display(location, 10, radius/3, radius/3, radius/3, 0.1);
        ParticleEffect.FLAME.display(location, 150, radius/1.5, radius/1.5, radius/1.5, 0.5);
        ParticleEffect.LAVA.display(location, 80, radius/2, radius/2, radius/2, 0.3);
        ParticleEffect.SMOKE_LARGE.display(location, 100, radius/1.5, radius/1.5, radius/1.5, 0.4);
        ParticleEffect.FIREWORKS_SPARK.display(location, 70, radius/2, radius/2, radius/2, 0.3);

        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        location.getWorld().playSound(location, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.7f);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.5f);

        int entitiesHit = 0;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(location, radius)) {
            if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId()) {
                LivingEntity livingEntity = (LivingEntity) entity;

                DamageHandler.damageEntity(entity, player, damage, this);

                Vector knockbackVector = entity.getLocation().toVector().subtract(location.toVector()).normalize();
                double knockbackStrength = (radius - entity.getLocation().distance(location)) / radius * 3.0 + 1.0;
                entity.setVelocity(knockbackVector.multiply(knockbackStrength).setY(1.2));

                entitiesHit++;
            }
        }

        Set<Block> blocks = new HashSet<>();
        int radiusInt = (int) Math.ceil(radius);

        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double distance = Math.sqrt(x*x + y*y + z*z);

                    if (distance <= radius) {
                        Block block = location.clone().add(x, y, z).getBlock();

                        if (shouldDestroyBlock(block)) {
                            blocks.add(block);
                        }
                    }
                }
            }
        }

        for (Block block : blocks) {
            originalBlocks.put(block, block.getType());
            block.setType(Material.AIR);
        }

        startRestorationAnimation(location.clone(), blocks, radius);

        affectedBlocks.addAll(blocks);

        plugin.getDatabaseManager().saveMeteorStatistics(player, radius, blocks.size(), entitiesHit, location);
    }

    private boolean shouldDestroyBlock(Block block) {
        if (isRegionProtected(block.getLocation())) {
            return false;
        }

        Material material = block.getType();

        if (material.equals(Material.AIR) || material.equals(Material.BEDROCK)) {
            return false;
        }

        if (material.isSolid() && material.isBlock()) {
            return true;
        }

        if (isFluid(material)) {
            return true;
        }

        if (isVegetation(material)) {
            return true;
        }

        return false;
    }

    private boolean isFluid(Material material) {
        return material.equals(Material.WATER) || 
               material.equals(Material.LAVA) ||
               material.name().contains("WATER") ||
               material.name().contains("LAVA");
    }

    private boolean isVegetation(Material material) {
        if (material.equals(Material.GRASS) || 
            material.equals(Material.TALL_GRASS) ||
            material.equals(Material.FERN) ||
            material.equals(Material.LARGE_FERN)) {
            return true;
        }

        if (material.name().contains("TULIP") ||
            material.name().contains("ORCHID") ||
            material.name().contains("ALLIUM") ||
            material.name().contains("POPPY") ||
            material.name().contains("DAISY") ||
            material.name().contains("CORNFLOWER") ||
            material.name().contains("LILY") ||
            material.equals(Material.DANDELION) ||
            material.equals(Material.SUNFLOWER) ||
            material.equals(Material.LILAC) ||
            material.equals(Material.ROSE_BUSH) ||
            material.equals(Material.PEONY)) {
            return true;
        }

        if (material.equals(Material.DEAD_BUSH) ||
            material.equals(Material.SEAGRASS) ||
            material.equals(Material.TALL_SEAGRASS) ||
            material.equals(Material.KELP) ||
            material.equals(Material.KELP_PLANT) ||
            material.equals(Material.SEA_PICKLE) ||
            material.equals(Material.SUGAR_CANE) ||
            material.equals(Material.BAMBOO) ||
            material.equals(Material.BAMBOO_SAPLING) ||
            material.equals(Material.SWEET_BERRY_BUSH) ||
            material.equals(Material.VINE) ||
            material.equals(Material.WHEAT) ||
            material.equals(Material.CARROTS) ||
            material.equals(Material.POTATOES) ||
            material.equals(Material.BEETROOTS) ||
            material.equals(Material.COCOA) ||
            material.equals(Material.MELON_STEM) ||
            material.equals(Material.PUMPKIN_STEM) ||
            material.equals(Material.ATTACHED_MELON_STEM) ||
            material.equals(Material.ATTACHED_PUMPKIN_STEM)) {
            return true;
        }

        if (material.equals(Material.BROWN_MUSHROOM) ||
            material.equals(Material.RED_MUSHROOM) ||
            material.equals(Material.CRIMSON_FUNGUS) ||
            material.equals(Material.WARPED_FUNGUS) ||
            material.equals(Material.CRIMSON_ROOTS) ||
            material.equals(Material.WARPED_ROOTS)) {
            return true;
        }

        if (material.name().contains("_SAPLING") ||
            material.name().contains("_SPROUTS")) {
            return true;
        }

        return false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "Meteor";
    }

    @Override
    public Element getElement() {
        return Element.AVATAR;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Shoot a meteor that explodes on impact, temporarily destroying blocks and damaging enemies.";
    }

    @Override
    public String getInstructions() {
        return "Left-click with the Meteor ability item to shoot a meteor.";
    }

    @Override
    public String getAuthor() {
        return "ElementCraft";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void load() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * АНИМИРОВАННОЕ ВОССТАНОВЛЕНИЕ - волнами от центра взрыва
     */
    private void startRestorationAnimation(Location center, Set<Block> blocksToRestore, double explosionRadius) {
        new BukkitRunnable() {
            @Override
            public void run() {
                restoreBlocksInWaves(center, blocksToRestore, explosionRadius);
            }
        }.runTaskLater(plugin, restorationDelay); // кфг
    }

    private void restoreBlocksInWaves(Location center, Set<Block> blocksToRestore, double explosionRadius) {
        Map<Integer, Set<Block>> blocksByHeight = new HashMap<>();
        
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        
        for (Block block : blocksToRestore) {
            int blockY = block.getY();
            blocksByHeight.computeIfAbsent(blockY, k -> new HashSet<>()).add(block);
            
            minY = Math.min(minY, blockY);
            maxY = Math.max(maxY, blockY);
        }

        final int finalMinY = minY;
        final int finalMaxY = maxY;

        new BukkitRunnable() {
            int currentY = finalMinY;

            @Override
            public void run() {
                Set<Block> currentLayerBlocks = blocksByHeight.get(currentY);
                
                if (currentLayerBlocks != null) {
                    for (Block block : currentLayerBlocks) {
                        Material originalData = originalBlocks.get(block);
                        if (originalData != null) {
                            block.setType(originalData);
                            
                            if (restorationEffects) {
                                Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
                                
                                block.getWorld().spawnParticle(
                                    org.bukkit.Particle.VILLAGER_HAPPY, 
                                    blockCenter, 3, 0.3, 0.3, 0.3, 0.1
                                );
                                
                                block.getWorld().spawnParticle(
                                    org.bukkit.Particle.END_ROD, 
                                    blockCenter, 2, 0.2, 0.2, 0.2, 0.05
                                );
                                
                                block.getWorld().playSound(
                                    blockCenter, 
                                    org.bukkit.Sound.BLOCK_GRASS_PLACE, 
                                    0.3f, 1.2f
                                );
                            }
                        }
                    }
                }
                
                currentY++;
                
                if (currentY > finalMaxY) {
                    cancel();
                    originalBlocks.clear(); // очистка памяти
                }
            }
        }.runTaskTimer(plugin, 0L, waveSpeed); // Используем конфиг для скорости волн
    }
}

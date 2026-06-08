package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 实体感知归类服务。
 * <p>
 * 这层专门负责把周围实体压成“自己 / 其他女仆 / 玩家 / 可爱动物 / 高风险怪 / 普通怪”几类，
 * 避免视觉服务和提示词拼装层各写一套判断。
 */
public final class MaidSoulEntityAwarenessService {
    private static final Set<String> CUTE_ANIMAL_TYPES = Set.of(
            "minecraft:cat",
            "minecraft:wolf",
            "minecraft:fox",
            "minecraft:rabbit",
            "minecraft:panda",
            "minecraft:axolotl",
            "minecraft:bee",
            "minecraft:allay",
            "minecraft:parrot",
            "minecraft:frog",
            "minecraft:camel"
    );

    private MaidSoulEntityAwarenessService() {
    }

    /**
     * 按拟人化交互需要，把主人周围实体整理成结构化摘要。
     */
    public static EnvironmentAwareness scan(EntityMaid maid, LivingEntity observer, double radius, int limitPerBucket) {
        List<Entity> nearby = observer.level().getEntities(
                observer,
                observer.getBoundingBox().inflate(radius),
                entity -> entity != observer && entity.isAlive()
        );

        ArrayList<String> players = new ArrayList<>();
        ArrayList<String> otherMaids = new ArrayList<>();
        ArrayList<String> cuteAnimals = new ArrayList<>();
        ArrayList<String> riskMobs = new ArrayList<>();
        ArrayList<String> normalHostiles = new ArrayList<>();
        ArrayList<String> others = new ArrayList<>();

        for (Entity entity : nearby) {
            if (entity == maid) {
                continue;
            }
            String summary = summarize(observer, entity);
            if (entity instanceof Player) {
                add(players, summary, limitPerBucket);
                continue;
            }
            if (entity instanceof EntityMaid otherMaid) {
                if (otherMaid.getUUID().equals(maid.getUUID())) {
                    continue;
                }
                add(otherMaids, summary, limitPerBucket);
                continue;
            }
            if (isCuteAnimal(entity)) {
                add(cuteAnimals, summary, limitPerBucket);
                continue;
            }
            if (entity instanceof Monster monster) {
                if (isHighRisk(monster)) {
                    add(riskMobs, summary, limitPerBucket);
                } else {
                    add(normalHostiles, summary, limitPerBucket);
                }
                continue;
            }
            add(others, summary, limitPerBucket);
        }

        return new EnvironmentAwareness(
                List.copyOf(players),
                List.copyOf(otherMaids),
                List.copyOf(cuteAnimals),
                List.copyOf(riskMobs),
                List.copyOf(normalHostiles),
                List.copyOf(others)
        );
    }

    private static boolean isCuteAnimal(Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String id = key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
        if (CUTE_ANIMAL_TYPES.contains(id)) {
            return true;
        }
        return entity instanceof Animal || entity instanceof AgeableMob;
    }

    private static boolean isHighRisk(Mob mob) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        String id = key == null ? "" : key.toString();
        return MaidSoulCommonConfig.HIGH_RISK_MOBS.get().stream().map(String::valueOf).anyMatch(id::equals);
    }

    private static String summarize(LivingEntity observer, Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String type = key == null ? "unknown" : key.toString();
        return "%s[%s](%.1f)".formatted(entity.getName().getString(), type, observer.distanceTo(entity));
    }

    private static void add(List<String> bucket, String value, int limit) {
        if (bucket.size() < limit) {
            bucket.add(value);
        }
    }

    /**
     * 环境分类结果。
     */
    public record EnvironmentAwareness(
            List<String> players,
            List<String> otherMaids,
            List<String> cuteAnimals,
            List<String> riskMobs,
            List<String> normalHostiles,
            List<String> others
    ) {
        /**
         * 压成稳定的一行摘要，方便写进原始视角文本。
         */
        public String compactSummary() {
            return "players=" + summarize(players)
                    + "; other_maids=" + summarize(otherMaids)
                    + "; cute_animals=" + summarize(cuteAnimals)
                    + "; risk_mobs=" + summarize(riskMobs)
                    + "; hostile_mobs=" + summarize(normalHostiles)
                    + "; others=" + summarize(others);
        }

        /**
         * 给视觉触发层快速判断这一帧是否有“值得主动开口”的社交或环境要素。
         */
        public boolean hasNotableEntity() {
            return !players.isEmpty() || !otherMaids.isEmpty() || !cuteAnimals.isEmpty() || !riskMobs.isEmpty();
        }

        private static String summarize(List<String> values) {
            return values.isEmpty() ? "none" : String.join(", ", values);
        }
    }
}

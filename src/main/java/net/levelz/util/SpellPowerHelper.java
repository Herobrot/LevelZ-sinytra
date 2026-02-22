package net.levelz.util;

import net.levelz.access.LevelManagerAccess;
import net.levelz.level.LevelManager;
import net.levelz.level.PlayerSkill;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class SpellPowerHelper {

    private static final String LEVELZ_NAMESPACE = "levelz";
    private static final int MAGIC_SKILL_ID = 5;

    // Cache para evitar lookups repetidos
    private static Boolean spellPowerAvailable = null;

    private static boolean isSpellPowerAvailable() {
        if (spellPowerAvailable == null) {
            spellPowerAvailable = Registries.ATTRIBUTE.get(Identifier.of("spell_power:fire")) != null;
            if (spellPowerAvailable) {
                System.out.println("[LevelZ] Spell Power attributes detected and available");
            }
        }
        return spellPowerAvailable;
    }

    public static void applySpellPowerBonuses(PlayerEntity playerEntity) {
        if (playerEntity.getWorld().isClient) return;
        if (isSpellPowerAvailable()) return;

        LevelManager levelManager = ((LevelManagerAccess) playerEntity).getLevelManager();
        PlayerSkill magicSkill = levelManager.getPlayerSkills().get(MAGIC_SKILL_ID);

        if (magicSkill == null) return;

        int magicLevel = magicSkill.getLevel();

        if (magicLevel <= 0) {
            // Si el nivel es 0, limpiar todos los bonuses
            clearSpellPowerBonuses(playerEntity);
            return;
        }

        // Escuelas de magia - 1 punto por nivel
        applyAttributeBonus(playerEntity, "spell_power:fire", "fire_bonus", magicLevel * 1.0f);
        applyAttributeBonus(playerEntity, "spell_power:frost", "frost_bonus", magicLevel * 1.0f);
        applyAttributeBonus(playerEntity, "spell_power:arcane", "arcane_bonus", magicLevel * 1.0f);
        applyAttributeBonus(playerEntity, "spell_power:healing", "healing_bonus", magicLevel * 1.0f);
        applyAttributeBonus(playerEntity, "spell_power:lightning", "lightning_bonus", magicLevel * 1.0f);
        applyAttributeBonus(playerEntity, "spell_power:soul", "soul_bonus", magicLevel * 1.0f);

        // Mecánicas de spell - valores según tu datapack
        applyAttributeBonus(playerEntity, "spell_power:critical_chance", "crit_chance_bonus", magicLevel * 5.0f);
        applyAttributeBonus(playerEntity, "spell_power:critical_damage", "crit_damage_bonus", magicLevel * 10.0f);
        applyAttributeBonus(playerEntity, "spell_power:haste", "haste_bonus", magicLevel * 10.0f);
        applyAttributeBonus(playerEntity, "spell_power:resistance.generic", "resistance_bonus", magicLevel * 0.1f);
    }

    private static void applyAttributeBonus(PlayerEntity playerEntity, String attributeId, String modifierSuffix, float value) {
        var attribute = Registries.ATTRIBUTE.get(Identifier.of(attributeId));
        if (attribute == null) return;

        var instance = playerEntity.getAttributeInstance((RegistryEntry<EntityAttribute>) attribute);
        if (instance == null) return;

        Identifier modifierId = Identifier.of(LEVELZ_NAMESPACE, modifierSuffix);

        // Remover modificador anterior si existe
        instance.getModifiers().stream()
                .filter(mod -> mod.id().equals(modifierId))
                .findFirst()
                .ifPresent(instance::removeModifier);

        // Agregar nuevo modificador solo si el valor es mayor a 0
        if (value > 0) {
            instance.addTemporaryModifier(new EntityAttributeModifier(
                    modifierId,
                    value,
                    EntityAttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    public static void clearSpellPowerBonuses(PlayerEntity playerEntity) {
        if (playerEntity.getWorld().isClient) return;
        if (isSpellPowerAvailable()) return;

        String[][] attributeData = {
                {"spell_power:fire", "fire_bonus"},
                {"spell_power:frost", "frost_bonus"},
                {"spell_power:arcane", "arcane_bonus"},
                {"spell_power:healing", "healing_bonus"},
                {"spell_power:lightning", "lightning_bonus"},
                {"spell_power:soul", "soul_bonus"},
                {"spell_power:critical_chance", "crit_chance_bonus"},
                {"spell_power:critical_damage", "crit_damage_bonus"},
                {"spell_power:haste", "haste_bonus"},
                {"spell_power:resistance.generic", "resistance_bonus"}
        };

        for (String[] data : attributeData) {
            var attribute = Registries.ATTRIBUTE.get(Identifier.of(data[0]));
            if (attribute != null) {
                var instance = playerEntity.getAttributeInstance((RegistryEntry<EntityAttribute>) attribute);
                if (instance != null) {
                    Identifier modifierId = Identifier.of(LEVELZ_NAMESPACE, data[1]);
                    instance.getModifiers().stream()
                            .filter(mod -> mod.id().equals(modifierId))
                            .findFirst()
                            .ifPresent(instance::removeModifier);
                }
            }
        }
    }
}
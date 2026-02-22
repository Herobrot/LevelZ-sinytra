package net.levelz.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.levelz.LevelzMain;
import net.levelz.init.ConfigInit;
import net.levelz.level.LevelManager;
import net.levelz.level.Skill;
import net.levelz.level.SkillAttribute;
import net.levelz.level.SkillBonus;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkillLoader implements SimpleSynchronousResourceReloadListener {

    private static final Logger LOGGER = LogManager.getLogger("LevelZ");

    private static final List<Integer> skillList = new ArrayList<>();

    @Override
    public Identifier getFabricId() {
        return LevelzMain.identifierOf("skill");
    }

    @Override
    public void reload(ResourceManager manager) {
        // clear skills
        LevelManager.SKILLS.clear();
        // clear bonuses
        LevelManager.BONUSES.clear();
        skillList.clear();

        // safety check
        AtomicInteger skillCount = new AtomicInteger();
        List<Integer> attributeIds = new ArrayList<>();

        // Determine which datapack to prioritize based on Spell Power availability
        boolean useRpgDatapack = shouldUseRpgDatapack();

        if (useRpgDatapack) {
            LOGGER.info("╔════════════════════════════════════════════════════════════════╗");
            LOGGER.info("║ Spell Power detected - Loading RPG skill set                   ║");
            LOGGER.info("║ School-based magic skills will be available                    ║");
            LOGGER.info("╚════════════════════════════════════════════════════════════════╝");
        } else {
            LOGGER.info("Loading standard skill set");
        }

        manager.findResources("skill", id -> id.getPath().endsWith(".json")).forEach((id, resourceRef) -> {
            try {
                String fileName = id.getPath();

                // Skip default.json if using RPG datapack, or skip default-rpg.json if not
                if (useRpgDatapack && fileName.endsWith("/default.json") && !ConfigInit.CONFIG.defaultSkills) {
                    LOGGER.debug("Skipping default.json (using RPG datapack)");
                    return;
                }
                if (!useRpgDatapack && fileName.endsWith("/default-rpg.json")) {
                    LOGGER.debug("Skipping default-rpg.json (Spell Power not available)");
                    return;
                }

                // Original skip logic for default skills
                if (!ConfigInit.CONFIG.defaultSkills && fileName.endsWith("/default.json")) {
                    return;
                }

                InputStream stream = resourceRef.getInputStream();
                JsonObject data = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();

                for (String mapKey : data.keySet()) {
                    JsonObject skillJsonObject = data.getAsJsonObject(mapKey);

                    int identification = skillJsonObject.get("id").getAsInt();
                    // replace check
                    if (skillJsonObject.has("replace") && skillJsonObject.get("replace").getAsBoolean()) {
                        skillList.add(identification);
                        if (LevelManager.SKILLS.containsKey(identification)) {
                            LevelManager.SKILLS.get(identification).getAttributes().forEach(attribute -> {
                                if (attribute.getId() != -1 && attributeIds.contains(attribute.getId())) {
                                    attributeIds.remove(attribute.getId());
                                }
                            });
                            LevelManager.SKILLS.remove(identification);
                            skillCount.getAndDecrement();
                        }
                    } else if (skillList.contains(identification)) {
                        continue;
                    }

                    // skill creation
                    String key = skillJsonObject.get("key").getAsString();
                    int maxLevel = skillJsonObject.get("level").getAsInt();
                    List<SkillAttribute> attributes = new ArrayList<>();

                    for (JsonElement attributeElement : skillJsonObject.getAsJsonArray("attributes")) {
                        JsonObject attributeJsonObject = attributeElement.getAsJsonObject();
                        String attributeType = attributeJsonObject.get("type").getAsString();

                        // Intentar obtener el atributo del registro
                        Optional<RegistryEntry.Reference<EntityAttribute>> entityAttribute = Registries.ATTRIBUTE.getEntry(Identifier.of(attributeType));

                        if (entityAttribute.isPresent()) {
                            int attributeId = -1;
                            if (attributeJsonObject.has("id")) {
                                attributeId = attributeJsonObject.get("id").getAsInt();
                            }
                            RegistryEntry<EntityAttribute> attibute = entityAttribute.get();
                            float baseValue = -10000.0f;
                            if (attributeJsonObject.has("base")) {
                                baseValue = attributeJsonObject.get("base").getAsFloat();
                            }
                            float levelValue = attributeJsonObject.get("value").getAsFloat();
                            EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation.valueOf(attributeJsonObject.get("operation").getAsString().toUpperCase());
                            attributes.add(new SkillAttribute(attributeId, attibute, baseValue, levelValue, operation));
                            if (attributeId != -1) {
                                attributeIds.add(attributeId);
                            }

                            // Enhanced logging for Spell Power attributes
                            if (attributeType.startsWith("spell_power:")) {
                                LOGGER.info("  ✓ Spell Power attribute '{}' registered for skill '{}' (base: {}, +{} per level, op: {})",
                                        attributeType, key, baseValue, levelValue, operation);
                            }
                        } else {
                            // Verificar si es un atributo de mod opcional (spell_power, etc.)
                            if (isOptionalModAttribute(attributeType)) {
                                LOGGER.warn("  ⚠ Optional mod attribute '{}' skipped in skill '{}' (mod not loaded or attributes not yet registered)",
                                        attributeType, key);
                            } else {
                                LOGGER.warn("  ✗ Attribute '{}' is not a usable attribute in skill '{}'.", attributeType, key);
                            }
                            continue;
                        }
                    }

                    if (skillJsonObject.has("bonus")) {
                        for (JsonElement attributeElement : skillJsonObject.getAsJsonArray("bonus")) {
                            JsonObject bonusJsonObject = attributeElement.getAsJsonObject();
                            String bonusKey = bonusJsonObject.get("key").getAsString();
                            int bonusLevel = bonusJsonObject.get("level").getAsInt();

                            if (!SkillBonus.BONUS_KEYS.contains(bonusKey)) {
                                LOGGER.warn("Bonus type {} is not a valid bonus type.", bonusKey);
                                continue;
                            }

                            LevelManager.BONUSES.put(bonusKey, new SkillBonus(bonusKey, identification, bonusLevel));
                        }
                    }

                    LevelManager.SKILLS.put(identification, new Skill(identification, key, maxLevel, attributes));

                    skillCount.getAndIncrement();
                }
            } catch (Exception e) {
                LOGGER.error("Error occurred while loading resource {}. {}", id.toString(), e.toString());
            }
        });

        for (int i = 0; i < skillCount.get(); i++) {
            if (!LevelManager.SKILLS.containsKey(i)) {
                throw new MissingResourceException("Missing skill with id " + i + "! Please add a skill with this id.", this.getClass().getName(), "LevelZ");
            }
        }
        for (int i = 0; i < attributeIds.size(); i++) {
            if (!attributeIds.contains(i)) {
                throw new MissingResourceException("Missing attribute with id " + i + "! Please add an attribute with this id.", this.getClass().getName(), "LevelZ");
            }
        }
        Map<Integer, Skill> sortedMap = new TreeMap<>(LevelManager.SKILLS);
        LevelManager.SKILLS.clear();
        LevelManager.SKILLS.putAll(sortedMap);

        LOGGER.info("✓ Skill loading complete: {} skills loaded successfully", skillCount.get());
    }

    /**
     * Determines whether to use the RPG datapack based on Spell Power availability.
     *
     * @return true if Spell Power is loaded AND its attributes are registered
     */
    private boolean shouldUseRpgDatapack() {
        // Check if Spell Power mod is loaded
        if (!FabricLoader.getInstance().isModLoaded("spell_power")) {
            return false;
        }

        // Check if key Spell Power attributes exist in the registry
        try {
            Identifier fireAttr = Identifier.of("spell_power", "fire");
            Identifier frostAttr = Identifier.of("spell_power", "frost");

            boolean fireExists = Registries.ATTRIBUTE.containsId(fireAttr);
            boolean frostExists = Registries.ATTRIBUTE.containsId(frostAttr);

            if (fireExists && frostExists) {
                LOGGER.info("  → Spell Power attributes detected in registry");
                return true;
            } else {
                LOGGER.warn("  → Spell Power mod loaded but attributes not yet registered");
                LOGGER.warn("     This may indicate loading order issues or incompatible versions");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("  → Error checking Spell Power attributes: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si un atributo pertenece a un mod opcional que puede no estar cargado
     */
    private static boolean isOptionalModAttribute(String attributeType) {
        // Lista de prefijos de mods opcionales conocidos
        String[] optionalModPrefixes = {
                "spell_power:",
                "spell_engine:",
                "wizards:",
                "paladins:",
                "archers:",
                "rogues:"
                // Agregar más mods opcionales según sea necesario
        };

        for (String prefix : optionalModPrefixes) {
            if (attributeType.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
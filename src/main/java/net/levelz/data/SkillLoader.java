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

    /** Namespace that identifies resources bundled inside the mod JAR. */
    private static final String MOD_NAMESPACE = "levelz";

    /**
     * Maps each built-in RPG companion filename suffix to the Fabric mod ID
     * that must be present for that file to be loaded.

     * To add support for a new companion mod, simply add one line here:
     *   RPG_COMPANION_FILES.put("/default-rpg-mymod.json", "my-mod-id");

     * No other code changes are required.
     */
    private static final Map<String, String> RPG_COMPANION_FILES = new LinkedHashMap<>();

    static {
        // Wizards mod -> fire / frost / arcane class skills (IDs 12–14)
        RPG_COMPANION_FILES.put("/default-rpg-wizards.json",  "wizards");
        // Paladins and Priests mod -> cleric / healing class skills (ID 15)
        RPG_COMPANION_FILES.put("/default-rpg-paladins.json", "paladins");
        // Rogues and Warriors -> rogue / berserk skills (ID 16-17)
        RPG_COMPANION_FILES.put("/default-rpg-rogues.json", "rogues");
        // Archers -> ranger skilll (ID 18)
        RPG_COMPANION_FILES.put("/default-rpg-ranger.json", "archers");
    }

    /**
     * The lowest skill ID reserved for companion mods.
     * IDs 0..(COMPANION_ID_START - 1) must always be contiguous.
     * IDs >= COMPANION_ID_START are optional and may be absent.

     * Keep this in sync with the lowest "id" value across all companion JSON files.
     */
    private static final int COMPANION_ID_START = 12;

    // Tracks IDs that have been "replaced" so duplicates from lower-priority files are skipped.
    private static final List<Integer> skillList = new ArrayList<>();

    @Override
    public Identifier getFabricId() {
        return LevelzMain.identifierOf("skill");
    }

    @Override
    public void reload(ResourceManager manager) {
        LevelManager.SKILLS.clear();
        LevelManager.BONUSES.clear();
        skillList.clear();

        AtomicInteger skillCount = new AtomicInteger();
        List<Integer> attributeIds = new ArrayList<>();

        boolean useRpgDatapack = isSpellPowerAvailable();
        Set<String> activeCompanionFiles = resolveActiveCompanionFiles(useRpgDatapack);

        logStartupSummary(useRpgDatapack, activeCompanionFiles);

        manager.findResources("skill", id -> id.getPath().endsWith(".json")).forEach((id, resourceRef) -> {
            try {
                String fileName  = id.getPath();
                String namespace = id.getNamespace();

                // ── Classify the resource ────────────────────────────────────────────────
                boolean isInternalBase      = isInternalBaseFile(namespace, fileName);
                boolean isInternalCompanion = isInternalCompanionFile(namespace, fileName);
                boolean isExternalDatapack  = !isInternalBase && !isInternalCompanion;

                // ── External datapacks ───────────────────────────────────────────────────
                // Fabric already places external datapacks earlier in the resource order
                // (higher priority), so they win over built-in files automatically.
                // The only restriction: a file literally named "default.json" is rejected
                // to prevent accidental shadowing of the built-in base file.
                if (isExternalDatapack) {
                    if (fileName.endsWith("/default.json")) {
                        LOGGER.warn("[LevelZ] External datapack '{}' is named 'default.json' — " +
                                "rename it to avoid shadowing the built-in skill file. Skipping.", id);
                        return;
                    }
                    LOGGER.debug("[LevelZ] Loading external datapack: {}", fileName);
                    loadSkillFile(id, resourceRef, skillCount, attributeIds);
                    return;
                }

                // ── Internal base files (default.json / default-rpg.json) ────────────────
                if (isInternalBase) {
                    if (useRpgDatapack) {
                        if (fileName.endsWith("/default.json")) {
                            LOGGER.debug("[LevelZ] Skipping default.json (RPG mode active)");
                            return;
                        }
                    } else {
                        if (fileName.endsWith("/default-rpg.json")) {
                            LOGGER.debug("[LevelZ] Skipping default-rpg.json (Spell Power not available)");
                            return;
                        }
                    }
                    if (!ConfigInit.CONFIG.defaultSkills) {
                        LOGGER.debug("[LevelZ] Skipping built-in '{}' (defaultSkills = false)", fileName);
                        return;
                    }
                    LOGGER.debug("[LevelZ] Loading built-in base file: {}", fileName);
                    loadSkillFile(id, resourceRef, skillCount, attributeIds);
                    return;
                }

                // ── Internal companion files (default-rpg-*.json) ────────────────────────
                if (isInternalCompanion) {
                    String matchedSuffix = RPG_COMPANION_FILES.keySet().stream()
                            .filter(fileName::endsWith)
                            .findFirst()
                            .orElse(null);

                    if (matchedSuffix == null) {
                        LOGGER.warn("[LevelZ] Unrecognised companion file '{}' — skipping.", fileName);
                        return;
                    }

                    if (!activeCompanionFiles.contains(matchedSuffix)) {
                        String requiredMod = RPG_COMPANION_FILES.get(matchedSuffix);
                        LOGGER.debug("[LevelZ] Skipping '{}' (mod '{}' not present or RPG mode inactive)",
                                fileName, requiredMod);
                        return;
                    }

                    LOGGER.debug("[LevelZ] Loading companion file: {}", fileName);
                    loadSkillFile(id, resourceRef, skillCount, attributeIds);
                }

            } catch (Exception e) {
                LOGGER.error("[LevelZ] Error processing resource {}: {}", id, e.toString());
            }
        });

        // ── Post-load validation ─────────────────────────────────────────────────────────
        validateSkillIds();
        validateAttributeIds(attributeIds);

        Map<Integer, Skill> sortedMap = new TreeMap<>(LevelManager.SKILLS);
        LevelManager.SKILLS.clear();
        LevelManager.SKILLS.putAll(sortedMap);

        LOGGER.info("[LevelZ] Skill loading complete: {} skills loaded, ids={}",
                skillCount.get(), LevelManager.SKILLS.keySet());
    }

    // ── Private helpers ──────────────────────────────────────────────────────────────────

    /**
     * Parses and registers all skills from a single JSON resource.
     */
    private void loadSkillFile(
            Identifier id,
            net.minecraft.resource.Resource resourceRef,
            AtomicInteger skillCount,
            List<Integer> attributeIds) {

        try {
            InputStream stream = resourceRef.getInputStream();
            JsonObject data = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();

            for (String mapKey : data.keySet()) {
                JsonObject skillJsonObject = data.getAsJsonObject(mapKey);
                int identification = skillJsonObject.get("id").getAsInt();

                // Handle replace flag
                if (skillJsonObject.has("replace") && skillJsonObject.get("replace").getAsBoolean()) {
                    skillList.add(identification);
                    if (LevelManager.SKILLS.containsKey(identification)) {
                        LevelManager.SKILLS.get(identification).getAttributes().forEach(attr -> {
                            if (attr.getId() != -1) {
                                attributeIds.remove(Integer.valueOf(attr.getId()));
                            }
                        });
                        LevelManager.SKILLS.remove(identification);
                        skillCount.getAndDecrement();
                    }
                } else if (skillList.contains(identification)) {
                    continue;
                }

                String key      = skillJsonObject.get("key").getAsString();
                int    maxLevel = skillJsonObject.get("level").getAsInt();
                List<SkillAttribute> attributes = new ArrayList<>();

                for (JsonElement attributeElement : skillJsonObject.getAsJsonArray("attributes")) {
                    JsonObject attrJson       = attributeElement.getAsJsonObject();
                    String     attributeType  = attrJson.get("type").getAsString();

                    Optional<RegistryEntry.Reference<EntityAttribute>> entityAttribute =
                            Registries.ATTRIBUTE.getEntry(Identifier.of(attributeType));

                    if (entityAttribute.isPresent()) {
                        int   attributeId = attrJson.has("id")   ? attrJson.get("id").getAsInt()      : -1;
                        float baseValue   = attrJson.has("base") ? attrJson.get("base").getAsFloat()   : -10000.0f;
                        float levelValue  = attrJson.get("value").getAsFloat();
                        EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation
                                .valueOf(attrJson.get("operation").getAsString().toUpperCase());

                        attributes.add(new SkillAttribute(attributeId, entityAttribute.get(), baseValue, levelValue, operation));

                        if (attributeId != -1) {
                            attributeIds.add(attributeId);
                        }
                        if (attributeType.startsWith("spell_power:")) {
                            LOGGER.info("  ✓ spell_power:'{}' → skill '{}' (base={}, +{}/lvl, op={})",
                                    attributeType, key, baseValue, levelValue, operation);
                        }
                    } else {
                        if (isOptionalModAttribute(attributeType)) {
                            LOGGER.warn("  ⚠ Optional attribute '{}' skipped in '{}' (mod not loaded)", attributeType, key);
                        } else {
                            LOGGER.warn("  ✗ Unknown attribute '{}' in '{}' — skipping.", attributeType, key);
                        }
                    }
                }

                if (skillJsonObject.has("bonus")) {
                    for (JsonElement bonusElement : skillJsonObject.getAsJsonArray("bonus")) {
                        JsonObject bonusJson  = bonusElement.getAsJsonObject();
                        String     bonusKey   = bonusJson.get("key").getAsString();
                        int        bonusLevel = bonusJson.get("level").getAsInt();

                        if (!SkillBonus.BONUS_KEYS.contains(bonusKey)) {
                            LOGGER.warn("[LevelZ] Bonus type '{}' is not a valid bonus type.", bonusKey);
                            continue;
                        }
                        LevelManager.BONUSES.put(bonusKey, new SkillBonus(bonusKey, identification, bonusLevel));
                    }
                }

                LevelManager.SKILLS.put(identification, new Skill(identification, key, maxLevel, attributes));
                skillCount.getAndIncrement();
            }
        } catch (Exception e) {
            LOGGER.error("[LevelZ] Failed to parse skill file {}: {}", id, e.toString());
        }
    }

    /**
     * Determines which companion file suffixes should be active.
     * Returns empty set when RPG mode is off.
     */
    private Set<String> resolveActiveCompanionFiles(boolean useRpgDatapack) {
        Set<String> active = new LinkedHashSet<>();
        if (!useRpgDatapack) return active;

        for (Map.Entry<String, String> entry : RPG_COMPANION_FILES.entrySet()) {
            String fileSuffix = entry.getKey();
            String modId      = entry.getValue();
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                LOGGER.info("[LevelZ] Companion mod '{}' detected → queuing '{}'", modId, fileSuffix);
                active.add(fileSuffix);
            } else {
                LOGGER.info("[LevelZ] Companion mod '{}' not present → skipping '{}'", modId, fileSuffix);
            }
        }
        return active;
    }

    /** True when the resource is one of the mod's own built-in base skill files. */
    private boolean isInternalBaseFile(String namespace, String fileName) {
        return namespace.equals(MOD_NAMESPACE)
                && (fileName.endsWith("/default.json") || fileName.endsWith("/default-rpg.json"));
    }

    /** True when the resource is one of the mod's own companion skill files. */
    private boolean isInternalCompanionFile(String namespace, String fileName) {
        if (!namespace.equals(MOD_NAMESPACE)) return false;
        return RPG_COMPANION_FILES.keySet().stream().anyMatch(fileName::endsWith);
    }

    /**
     * Validates loaded skill IDs.

     * Base skill IDs (0 to COMPANION_ID_START - 1) must be contiguous.
     * Companion skill IDs (>= COMPANION_ID_START) are optional — gaps are allowed
     * when the companion mod is not installed.
     */
    private void validateSkillIds() {
        if (LevelManager.SKILLS.isEmpty()) return;

        // Validate base range is fully present
        for (int i = 0; i < COMPANION_ID_START; i++) {
            if (!LevelManager.SKILLS.containsKey(i)) {
                throw new MissingResourceException(
                        "Missing base skill with id " + i + ". IDs 0–" + (COMPANION_ID_START - 1) +
                                " must all be present.",
                        this.getClass().getName(), "LevelZ");
            }
        }

        // Log which optional companion IDs are absent (not an error)
        int maxId = Collections.max(LevelManager.SKILLS.keySet());
        for (int i = COMPANION_ID_START; i <= maxId; i++) {
            if (!LevelManager.SKILLS.containsKey(i)) {
                LOGGER.debug("[LevelZ] Companion skill id {} is absent (companion mod not loaded)", i);
            }
        }
    }

    /** Validates that numbered attribute IDs are contiguous from 0. */
    private void validateAttributeIds(List<Integer> attributeIds) {
        for (int i = 0; i < attributeIds.size(); i++) {
            if (!attributeIds.contains(i)) {
                throw new MissingResourceException(
                        "Missing attribute with id " + i + "! Please add an attribute with this id.",
                        this.getClass().getName(), "LevelZ");
            }
        }
    }

    private void logStartupSummary(boolean useRpgDatapack, Set<String> activeCompanionFiles) {
        if (useRpgDatapack) {
            LOGGER.info("[LevelZ] ── RPG mode active (Spell Power detected) ──────────────────");
            LOGGER.info("[LevelZ]   Base     : default-rpg.json");
            if (activeCompanionFiles.isEmpty()) {
                LOGGER.info("[LevelZ]   Companions: (none — no companion mods detected)");
            } else {
                activeCompanionFiles.forEach(f -> LOGGER.info("[LevelZ]   Companion: {}", f));
            }
            LOGGER.info("[LevelZ] ────────────────────────────────────────────────────────────");
        } else {
            LOGGER.info("[LevelZ] Standard mode — loading default.json");
        }
    }

    /**
     * Returns true if Spell Power is installed AND its core attributes are already
     * present in the attribute registry.
     */
    private boolean isSpellPowerAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("spell_power")) {
            return false;
        }
        try {
            boolean fireExists  = Registries.ATTRIBUTE.containsId(Identifier.of("spell_power", "fire"));
            boolean frostExists = Registries.ATTRIBUTE.containsId(Identifier.of("spell_power", "frost"));
            if (fireExists && frostExists) {
                LOGGER.info("[LevelZ] Spell Power attributes confirmed in registry");
                return true;
            }
            LOGGER.warn("[LevelZ] Spell Power loaded but attributes not yet registered (fire={}, frost={})",
                    fireExists, frostExists);
            return false;
        } catch (Exception e) {
            LOGGER.error("[LevelZ] Error checking Spell Power registry: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns true if the attribute namespace belongs to a known optional mod.
     * Used to emit a friendlier warning instead of a generic error.
     */
    private static boolean isOptionalModAttribute(String attributeType) {
        String[] optionalModPrefixes = {
                "spell_power:",
                "spell_engine:",
                "wizards:",
                "paladins:",
                "archers:",
                "rogues:"
        };
        for (String prefix : optionalModPrefixes) {
            if (attributeType.startsWith(prefix)) return true;
        }
        return false;
    }
}
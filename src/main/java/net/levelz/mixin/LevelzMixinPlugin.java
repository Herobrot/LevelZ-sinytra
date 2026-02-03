package net.levelz.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class LevelzMixinPlugin implements IMixinConfigPlugin {

    private static Boolean isRunningOnConnector = null;

    private static boolean isRunningOnConnector() {
        if (isRunningOnConnector == null) {
            try {
                // Check if Sinytra Connector is present
                Class.forName("org.sinytra.connector.ConnectorEarlyLoader");
                isRunningOnConnector = true;
            } catch (ClassNotFoundException e) {
                try {
                    // Alternative check for NeoForge
                    Class.forName("net.neoforged.fml.ModList");
                    isRunningOnConnector = true;
                } catch (ClassNotFoundException ex) {
                    isRunningOnConnector = false;
                }
            }
        }
        return isRunningOnConnector;
    }

    private static boolean isModLoaded(String modId) {
        try {
            // Try Fabric first
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            return (boolean) instance.getClass().getMethod("isModLoaded", String.class).invoke(instance, modId);
        } catch (Exception e) {
            // Try Forge/NeoForge
            try {
                Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
                Object instance = modList.getMethod("get").invoke(null);
                return (boolean) instance.getClass().getMethod("isLoaded", String.class).invoke(instance, modId);
            } catch (Exception ex) {
                // If both fail, assume mod is not loaded
                return false;
            }
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!isModLoaded("trinkets")
                && (mixinClassName.equals("net.levelz.mixin.compat.TrinketItemMixin")
                    || mixinClassName.equals("net.levelz.mixin.compat.SurvivalTrinketSlotMixin")))
            return false;

        if (mixinClassName.contains("LevelManagerCompatMixin") && !isModLoaded("create") && !isModLoaded("computercraft"))
            return false;

        if (mixinClassName.contains("FishingRodItemMixin") && isModLoaded("go-fish"))
            return false;

        if (mixinClassName.contains("ArmorItemMixin") && isModLoaded("cardboard"))
            return false;

        if (mixinClassName.contains("AnvilScreenHandlerMixin") && isModLoaded("limitless"))
            return false;

        if (mixinClassName.contains("BackpackItemMixin") && !isModLoaded("inmis"))
            return false;

        if (mixinClassName.contains("EasyMagicEnchantingTableMixin") && !isModLoaded("easymagic"))
            return false;

        if (mixinClassName.contains("EasyAnvilsAnvilMixin") && !isModLoaded("easyanvils"))
            return false;

        if (mixinClassName.contains("CosmeticArmorMixin") && !isModLoaded("cosmetic-armor"))
            return false;

        if (mixinClassName.contains("ChopResultMixin") && !isModLoaded("treechop"))
            return false;

        if (mixinClassName.contains("SmithingAnvilScreenHandlerMixin") && !isModLoaded("alloygery"))
            return false;

        if (mixinClassName.contains("TieredCompatMixin") && isModLoaded("tiered"))
            return false;

        if (mixinClassName.contains("SpellPowerCompatMixin") && !isModLoaded("spell_power"))
            return false;

        // Platform-specific ItemStack mixins
        // ItemStackServerMixin uses ServerPlayerEntity (Fabric only)
        // ItemStackServerConnectorMixin uses LivingEntity (Connector/Forge only)
        if (mixinClassName.contains("ItemStackServerConnectorMixin") && !isRunningOnConnector())
            return false;
        if (mixinClassName.contains("ItemStackServerMixin") && !mixinClassName.contains("Connector") && isRunningOnConnector())
            return false;

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

}
package net.levelz.init;

import java.lang.reflect.Method;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import ht.treechop.api.TreeChopEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.levelz.LevelzMain;
import net.levelz.access.LevelManagerAccess;
import net.levelz.level.LevelManager;

public class CompatInit {

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            Placeholders.register(LevelzMain.identifierOf("playerlevel"), (ctx, arg) -> {
                if (ctx.hasPlayer()) {
                    return PlaceholderResult.value(Integer.toString(((LevelManagerAccess) ctx.player()).getLevelManager().getOverallLevel()));
                } else {
                    return PlaceholderResult.invalid("No player!");
                }
            });
        }

        if (FabricLoader.getInstance().isModLoaded("treechop")) {
            try {
                // Try Fabric TreeChop API first
                Class.forName("ht.treechop.api.TreeChopEvents");
                initTreeChopFabric();
            } catch (ClassNotFoundException e) {
                // Try Forge TreeChop API
                try {
                    Class.forName("ht.treechop.api.ChopEvent");
                    initTreeChopForge();
                } catch (Exception ex) {
                    System.out.println("[LevelZ] TreeChop detected but API not compatible, skipping integration");
                }
            }
        }

        if (FabricLoader.getInstance().isModLoaded("spell_power")) {
            initSpellPowerCompat();
        }
    }

    private static void initTreeChopFabric() {
        // Original Fabric implementation
        TreeChopEvents.BEFORE_CHOP.register((world, player, pos, state, chopData) -> {
            LevelManager levelManager = ((LevelManagerAccess) player).getLevelManager();
            if (!levelManager.hasRequiredItemLevel(player.getMainHandStack().getItem())) {
                player.getWorld().breakBlock(pos, false);
                return false;
            } else if (!levelManager.hasRequiredMiningLevel(world.getBlockState(pos).getBlock())) {
                player.getWorld().breakBlock(pos, false);
                return false;
            }
            return true;
        });
    }

    private static void initTreeChopForge() throws Exception {
        try {
            System.out.println("[LevelZ] Attempting to register Forge/NeoForge TreeChop handler...");

            // Try NeoForge first, then fall back to old Forge
            Object eventBus = null;
            Class<?> eventPriorityClass = null;

            try {
                // NeoForge event bus
                Class<?> neoForgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
                eventBus = neoForgeClass.getField("EVENT_BUS").get(null);
                eventPriorityClass = Class.forName("net.neoforged.bus.api.EventPriority");
                System.out.println("[LevelZ] Using NeoForge event bus");
            } catch (ClassNotFoundException e) {
                // Fall back to old Forge event bus
                Class<?> forgeEventBusClass = Class.forName("net.minecraftforge.common.MinecraftForge");
                eventBus = forgeEventBusClass.getField("EVENT_BUS").get(null);
                eventPriorityClass = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
                System.out.println("[LevelZ] Using MinecraftForge event bus");
            }

            Class<?> startChopEventClass = Class.forName("ht.treechop.api.ChopEvent$StartChopEvent");
            System.out.println("[LevelZ] Found StartChopEvent class: " + startChopEventClass.getName());

            TreeChopForgeCompat handler = new TreeChopForgeCompat();

            Object typedConsumer = createTypedConsumer(startChopEventClass, handler);

            Object normalPriority = eventPriorityClass.getField("NORMAL").get(null);

            Method addListenerMethod = eventBus.getClass().getMethod(
                    "addListener",
                    eventPriorityClass,
                    boolean.class,
                    Class.class,
                    java.util.function.Consumer.class
            );

            System.out.println("[LevelZ] About to invoke addListener...");
            addListenerMethod.invoke(eventBus, normalPriority, false, startChopEventClass, typedConsumer);

            System.out.println("[LevelZ] Successfully registered Forge/NeoForge TreeChop listener");

        } catch (Exception e) {
            System.out.println("[LevelZ] EXCEPTION in initTreeChopForge: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("[LevelZ] Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Initializes Spell Power compatibility detection.
     *
     * NOTE: This only verifies that the Spell Power API classes exist.
     * The actual integration is datapack-based and happens automatically
     * when SkillLoader detects Spell Power and loads default-rpg.json.
     *
     * No runtime attribute modification is performed here - that approach
     * was proven to be incompatible with LevelZ's architecture.
     */
    private static void initSpellPowerCompat() {
        try {
            // Verify core API classes exist
            Class.forName("net.spell_power.api.SpellSchools");
            Class.forName("net.spell_power.api.SpellPowerMechanics");

            System.out.println("[LevelZ] Spell Power API detected");
        } catch (ClassNotFoundException e) {
            System.out.println("[LevelZ-WARNING]  Spell Power mod detected but API classes not found");
            System.out.println("[LevelZ]   This may indicate an incompatible version");
            System.out.println("[LevelZ]   School-based magic skills will not be available");
        }
    }

    @SuppressWarnings({"rawtypes"})
    private static Object createTypedConsumer(Class<?> eventClass, TreeChopForgeCompat handler) {
        return (java.util.function.Consumer) handler::onStartChop;
    }
}
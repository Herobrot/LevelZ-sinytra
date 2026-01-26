package net.levelz.init;

import java.lang.reflect.Method;
import net.levelz.access.LevelManagerAccess;
import net.levelz.level.LevelManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TreeChopForgeCompat {

    public void onStartChop(Object event) {
        // Filter to only handle StartChopEvent
        if (!event.getClass().getName().equals("ht.treechop.api.ChopEvent$StartChopEvent")) {
            return;
        }
        try {
            // Use reflection to access event methods - search through class hierarchy
            Class<?> eventClass = event.getClass();

            // getPlayer and getLevel may be in parent class (ChopEvent)
            Method getPlayerMethod = findMethod(eventClass, "getPlayer");
            Method getLevelMethod = findMethod(eventClass, "getLevel");
            Method getChoppedBlockPosMethod = findMethod(eventClass, "getChoppedBlockPos");

            // setCanceled is from ICancellableEvent interface (NeoForge) or Event class (Forge)
            Method setCanceledMethod = findMethod(eventClass, "setCanceled", boolean.class);

            if (getPlayerMethod == null || getLevelMethod == null || getChoppedBlockPosMethod == null) {
                System.out.println("[LevelZ] TreeChop: Could not find required methods on event class");
                return;
            }

            Object player = getPlayerMethod.invoke(event);
            if (player == null) return;

            Object level = getLevelMethod.invoke(event);
            Object blockPos = getChoppedBlockPosMethod.invoke(event);

            // Cast to Fabric types (works through Connector's remapping)
            PlayerEntity fabricPlayer = (PlayerEntity) player;
            World fabricWorld = (World) level;
            BlockPos fabricBlockPos = (BlockPos) blockPos;

            LevelManager levelManager = ((LevelManagerAccess) fabricPlayer).getLevelManager();
            boolean shouldCancel = false;

            if (!levelManager.hasRequiredItemLevel(fabricPlayer.getMainHandStack().getItem())) {
                shouldCancel = true;
            } else if (!levelManager.hasRequiredMiningLevel(fabricWorld.getBlockState(fabricBlockPos).getBlock())) {
                shouldCancel = true;
            }

            if (shouldCancel) {
                // Break the block without dropping items (same as Fabric version)
                fabricWorld.breakBlock(fabricBlockPos, false);
                // Cancel the TreeChop event
                if (setCanceledMethod != null) {
                    setCanceledMethod.invoke(event, true);
                }
            }
        } catch (Exception e) {
            System.out.println("[LevelZ] TreeChop event handling error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find a method in the class hierarchy, including interfaces
     */
    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        // First try direct method lookup
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            // Method not found directly, search hierarchy
        }

        // Search through class hierarchy
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, paramTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                // Continue searching
            }

            // Check interfaces
            for (Class<?> iface : current.getInterfaces()) {
                Method method = findMethodInInterface(iface, name, paramTypes);
                if (method != null) {
                    return method;
                }
            }

            current = current.getSuperclass();
        }
        return null;
    }

    private Method findMethodInInterface(Class<?> iface, String name, Class<?>... paramTypes) {
        try {
            return iface.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            // Check parent interfaces
            for (Class<?> parent : iface.getInterfaces()) {
                Method method = findMethodInInterface(parent, name, paramTypes);
                if (method != null) {
                    return method;
                }
            }
        }
        return null;
    }
}

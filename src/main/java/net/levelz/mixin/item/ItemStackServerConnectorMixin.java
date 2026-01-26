package net.levelz.mixin.item;

import net.levelz.util.BonusHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Forge/Connector-compatible version of ItemStackServerMixin.
 * Uses MojMap method name (hurtAndBreak) and LivingEntity parameter.
 */
@Mixin(ItemStack.class)
public class ItemStackServerConnectorMixin {

    // Use MojMap method name directly since Connector doesn't remap method names in descriptors
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void damageMixin(int amount, ServerWorld world, @Nullable LivingEntity entity, Consumer<Item> breakCallback, CallbackInfo info) {
        if (entity instanceof PlayerEntity player && BonusHelper.itemDamageChanceBonus(player)) {
            info.cancel();
        }
    }

}

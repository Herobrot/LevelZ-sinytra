package net.levelz.mixin.compat;

import net.levelz.util.SpellPowerHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class SpellPowerCompatMixin extends LivingEntity {

    protected SpellPowerCompatMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void levelz$tickSpellPower(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player.age % 20 == 0) { // Cada segundo
            try {
                SpellPowerHelper.applySpellPowerBonuses(player);
            } catch (Exception e) {
                // Silenciar errores para evitar crashes
            }
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void levelz$onDeathSpellPower(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient) {
            try {
                SpellPowerHelper.clearSpellPowerBonuses(player);
            } catch (Exception e) {
                // Silenciar errores
            }
        }
    }
}
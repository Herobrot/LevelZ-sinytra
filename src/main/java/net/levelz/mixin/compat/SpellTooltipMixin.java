package net.levelz.mixin.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(value = net.spell_engine.client.gui.SpellTooltip.class, remap = false)
public class SpellTooltipMixin {

    @Inject(
            method = "addSpellLines",
            at = @At("RETURN")
    )
    private static void fixAccessTooltipOrder(
            ItemStack itemStack, TooltipType tooltipType, List<Text> lines, CallbackInfo ci
    ) {
        // Buscamos las líneas de acceso de SpellEngine
        List<Text> accessLines = new ArrayList<>();
        List<Integer> indicesToRemove = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String key = lines.get(i).getString();
            // Identificamos las líneas por su clave de traducción
            if (isSpellAccessLine(lines.get(i))) {
                // Si la línea anterior es un separador vacío, lo removemos también
                if (!indicesToRemove.isEmpty() || (i > 0 && lines.get(i - 1).getString().isBlank())) {
                    indicesToRemove.add(i - 1);
                }
                indicesToRemove.add(i);
                accessLines.add(lines.get(i));
            }
        }

        if (accessLines.isEmpty()) return;

        // Removemos de atrás hacia adelante para no desplazar índices
        indicesToRemove.stream()
                .sorted(Comparator.reverseOrder())
                .forEach(i -> lines.remove((int) i));

        // Las añadimos al final con su separador
        lines.add(Text.literal(""));
        lines.addAll(accessLines);
    }

    @Unique
    private static boolean isSpellAccessLine(Text text) {
        String content = text.getString();
        return content.equals(Text.translatable("spell.tooltip.container.access.any").getString())
                || content.equals(Text.translatable("spell.tooltip.container.access.spell").getString())
                || content.equals(Text.translatable("spell.tooltip.container.access.archery").getString());
    }
}

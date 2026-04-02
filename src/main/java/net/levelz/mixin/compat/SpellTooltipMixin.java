package net.levelz.mixin.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = net.spell_engine.client.gui.SpellTooltip.class)
public class SpellTooltipMixin {

    @Inject(
            method = "addSpellLines",
            at = @At("RETURN")
    )
    private static void fixAccessTooltipOrder(
            ItemStack itemStack, TooltipType tooltipType, List<Text> lines, CallbackInfo ci
    ) {
        List<Text> accessLines = new ArrayList<>();
        List<Integer> indicesToRemove = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            Text currentText = lines.get(i);

            if (isSpellAccessLine(currentText)) {
                // Guardamos la línea de texto útil
                indicesToRemove.add(i);
                accessLines.add(currentText);

                // Si SpellEngine dejó un espacio vacío justo antes, lo marcamos para eliminar
                // y que no quede un hueco suelto en el medio del tooltip.
                if (i > 0 && lines.get(i - 1).getString().isBlank() && !indicesToRemove.contains(i - 1)) {
                    indicesToRemove.add(i - 1);
                }
            }
        }

        if (accessLines.isEmpty()) return;

        // Removemos de atrás hacia adelante para no alterar los índices durante el borrado
        for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
            lines.remove((int) indicesToRemove.get(i));
        }

        // Insertamos las líneas en la parte superior, justo debajo del nombre del ítem (índice 1)
        if (!lines.isEmpty()) {
            lines.addAll(1, accessLines);
            // Opcional: Agregamos un espacio en blanco debajo de estas líneas para separarlas
            // visualmente de las estadísticas de daño, velocidad o atributos que sigan.
            lines.add(1 + accessLines.size(), Text.literal(""));
        } else {
            lines.addAll(accessLines);
        }
    }

    @Unique
    private static boolean isSpellAccessLine(Text text) {
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            return key.equals("spell.tooltip.container.access.any")
                    || key.equals("spell.tooltip.container.access.spell")
                    || key.equals("spell.tooltip.container.access.archery")
                    || key.equals("spell.tooltip.container.access.rogues")
                    || key.startsWith("spell.tooltip.container.access.tag.");
        }
        return false;
    }
}
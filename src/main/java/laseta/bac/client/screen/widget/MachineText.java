package laseta.bac.client.screen.widget;

import laseta.bac.BetterAutoCrafter;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;

/**
 * GUI texts that can be worded for each machine ("Pulls ingredients" for the crafter, "Pulls items" for the mover).
 * {@code get("side_mode.input.desc")} uses "gui.better_auto_crafter.&lt;machine id&gt;.side_mode.input.desc" when the
 * language has it, otherwise the shared "gui.better_auto_crafter.side_mode.input.desc". A new machine only needs
 * translations for the texts it words differently.
 */
public final class MachineText {
    private static final String PREFIX = "gui." + BetterAutoCrafter.MODID + ".";

    private final String machinePrefix;

    public MachineText(Block machine) {
        this.machinePrefix = PREFIX + BuiltInRegistries.BLOCK.getKey(machine).getPath() + ".";
    }

    public MutableComponent get(String key, Object... args) {
        String specific = machinePrefix + key;
        return Component.translatable(I18n.exists(specific) ? specific : PREFIX + key, args);
    }
}

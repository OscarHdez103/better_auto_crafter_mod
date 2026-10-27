package laseta.bac.machine;

import java.util.function.Supplier;

import laseta.bac.upgrade.TranslocationMode;
import net.minecraft.world.item.ItemStack;

/**
 * A kind of item only some machines move besides their inputs and outputs, through faces of its own: e.g. the
 * smelter's fuel and what is left of it, or the brewer's starting bottles. Each face of the machine is on or off for
 * it, set in the machine's own tab; a face can be on for several channels and have any side mode too.
 *
 * <p>Like the inputs and outputs, a channel's faces move to the Translocator when a translocation upgrade of its
 * direction is installed (pulled channels with a Pull Upgrade, pushed ones with a Push Upgrade). Multi translocation
 * upgrades only replace the side configuration's faces: channels keep the machine's own faces with them.</p>
 *
 * @param name      saved under this key, and used in the GUI texts' keys ("channel.&lt;name&gt;")
 * @param direction whether the machine pulls from or pushes into these faces
 * @param color     RGB colour of the faces that are on, in the GUI
 * @param icon      item shown for the channel in the GUI
 */
public record FaceChannel(String name, TranslocationMode direction, int color, Supplier<ItemStack> icon) {
}

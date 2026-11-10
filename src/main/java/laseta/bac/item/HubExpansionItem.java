package laseta.bac.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

/**
 * Unlocks one more tab of a Better Auto Hub, used on its block or on a locked tab of its GUI (see
 * {@link laseta.bac.block.entity.BetterAutoHubBlockEntity#expand}). The hub keeps it when broken.
 */
public class HubExpansionItem extends Item {
    public HubExpansionItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.better_auto_crafter.hub_expansion.desc").withStyle(ChatFormatting.GRAY));
    }
}

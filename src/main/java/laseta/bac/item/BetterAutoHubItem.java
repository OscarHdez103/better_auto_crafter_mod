package laseta.bac.item;

import java.util.List;

import laseta.bac.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/** The Better Auto Hub's item: it keeps the Hub Expansions used on the hub, and says how many. */
public class BetterAutoHubItem extends BlockItem {
    public BetterAutoHubItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        int expansions = stack.getOrDefault(ModDataComponents.HUB_EXPANSIONS.get(), 0);
        if (expansions > 0) {
            tooltip.add(Component.translatable("item.better_auto_crafter.better_auto_hub.expansions", expansions)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}

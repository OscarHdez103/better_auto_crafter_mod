package laseta.bac.item;

import java.util.List;

import laseta.bac.upgrade.UpgradeInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

/** Upgrade that makes a machine repeat its operation once more per installed item, every time it runs. */
public class CoProcessorUpgradeItem extends Item {
    public CoProcessorUpgradeItem(Properties properties) {
        super(properties);
    }

    /** Operations per run with a stack of co-processor upgrades installed (1 for anything else). */
    public static int getOperations(ItemStack stack) {
        return stack.getItem() instanceof CoProcessorUpgradeItem ? 1 + stack.getCount() : 1;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.better_auto_crafter.co_processor_upgrade.desc").withStyle(ChatFormatting.GRAY));
        // The whole hovered stack, so a slot of co-processors shows how many operations it makes.
        tooltip.add(Component.translatable("item.better_auto_crafter.co_processor_upgrade.effect", getOperations(stack))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        return installed != InteractionResult.PASS ? installed : super.useOn(context);
    }
}

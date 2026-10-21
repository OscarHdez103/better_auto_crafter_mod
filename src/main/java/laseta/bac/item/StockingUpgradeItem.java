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

/**
 * Upgrade that stops a machine from pushing into an output inventory that already holds enough of what it pushes.
 * Every installed item adds {@link #getStockPerItem()} to that limit; higher tiers are crafted from 8 of the previous
 * one, so they count 8 times as much.
 */
public class StockingUpgradeItem extends Item {
    private final int stockPerItem;

    public StockingUpgradeItem(int stockPerItem, Properties properties) {
        super(properties);
        this.stockPerItem = stockPerItem;
    }

    public int getStockPerItem() {
        return stockPerItem;
    }

    /** The stock limit a stack of stocking upgrades sets, or 0 (no limit) for anything else. */
    public static int getStockLimit(ItemStack stack) {
        return stack.getItem() instanceof StockingUpgradeItem upgrade ? stack.getCount() * upgrade.stockPerItem : 0;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.better_auto_crafter.stocking_upgrade.desc").withStyle(ChatFormatting.GRAY));
        // The whole hovered stack, so a slot of stocking upgrades shows the limit it sets.
        tooltip.add(Component.translatable("item.better_auto_crafter.stocking_upgrade.checks_for", getStockLimit(stack), stockPerItem)
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        return installed != InteractionResult.PASS ? installed : super.useOn(context);
    }
}

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

/** Brewer upgrade: the brewer fills empty glass bottles with water itself, so its starter faces supply those instead. */
public class InfiniteWaterUpgradeItem extends Item {
    public InfiniteWaterUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.better_auto_crafter.infinite_water_upgrade.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        return installed != InteractionResult.PASS ? installed : super.useOn(context);
    }
}

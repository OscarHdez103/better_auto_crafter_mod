package laseta.bac.item;

import java.util.List;

import laseta.bac.registry.ModDataComponents;
import laseta.bac.upgrade.UpgradeInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

/**
 * An upgrade set to an item, in the machine's own tab: the box next to its slot takes a copy of the item clicked on it
 * (nothing is used up). The setting stays on the upgrade, so it keeps it when moved to another machine.
 */
public abstract class ItemSettingUpgradeItem extends Item {
    protected ItemSettingUpgradeItem(Properties properties) {
        super(properties);
    }

    /** The item {@code upgrade} is set to (count 1), or EMPTY if it isn't set (or isn't such an upgrade). */
    public static ItemStack getSetting(ItemStack upgrade) {
        if (!(upgrade.getItem() instanceof ItemSettingUpgradeItem)) {
            return ItemStack.EMPTY;
        }
        ItemContainerContents contents = upgrade.get(ModDataComponents.UPGRADE_SETTING.get());
        return contents == null ? ItemStack.EMPTY : contents.copyOne();
    }

    /** Sets {@code upgrade} to {@code setting} (any count: one is kept), or clears it with EMPTY. */
    public static void setSetting(ItemStack upgrade, ItemStack setting) {
        if (setting.isEmpty()) {
            upgrade.remove(ModDataComponents.UPGRADE_SETTING.get());
        } else {
            upgrade.set(ModDataComponents.UPGRADE_SETTING.get(), ItemContainerContents.fromItems(List.of(setting.copyWithCount(1))));
        }
    }

    /** Whether the upgrade may be set to {@code candidate}. */
    public abstract boolean canBeSetTo(ItemStack candidate);

    /** Lang key of the line saying what the upgrade does with the item it is set to. */
    protected abstract String descriptionKey();

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(descriptionKey()).withStyle(ChatFormatting.GRAY));
        ItemStack setting = getSetting(stack);
        tooltip.add(setting.isEmpty()
                ? Component.translatable("item.better_auto_crafter.item_setting_upgrade.not_set").withStyle(ChatFormatting.RED)
                : Component.translatable("item.better_auto_crafter.item_setting_upgrade.set_to", setting.getHoverName())
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        return installed != InteractionResult.PASS ? installed : super.useOn(context);
    }
}

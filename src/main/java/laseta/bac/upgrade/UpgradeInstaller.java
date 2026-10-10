package laseta.bac.upgrade;

import laseta.bac.block.entity.BetterAutoCrafterBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Sneak + use with an upgrade on a Better Auto Crafter installs one upgrade without opening the GUI. If the slot
 * holds a different upgrade (e.g. a basic pull upgrade when installing a dimensional one), that one is swapped out
 * and given back to the player.
 */
public final class UpgradeInstaller {
    private UpgradeInstaller() {
    }

    /** @return PASS if this isn't a sneak-use of an upgrade on a crafter, so the item's normal use runs instead */
    public static InteractionResult tryInstall(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack held = context.getItemInHand();
        UpgradeSlotType type = UpgradeSlotType.forStack(held);
        if (player == null || !player.isSecondaryUseActive() || type == null
                || !(level.getBlockEntity(context.getClickedPos()) instanceof BetterAutoCrafterBlockEntity crafter)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        UpgradeInventory upgrades = crafter.getUpgrades();
        int slot = type.ordinal();
        ItemStack installed = upgrades.getStackInSlot(slot);
        ItemStack returned = ItemStack.EMPTY;
        if (installed.isEmpty() || ItemStack.isSameItemSameComponents(installed, held)) {
            if (installed.getCount() >= upgrades.getSlotLimit(slot)) {
                player.displayClientMessage(Component.translatable("gui.better_auto_crafter.upgrade_slot.full",
                        type.getDisplayName()), true);
                return InteractionResult.CONSUME;
            }
            upgrades.setStackInSlot(slot, held.copyWithCount(installed.getCount() + 1));
        } else {
            returned = installed;
            upgrades.setStackInSlot(slot, held.copyWithCount(1));
        }
        held.consume(1, player);
        if (!returned.isEmpty() && !player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
        return InteractionResult.CONSUME;
    }
}

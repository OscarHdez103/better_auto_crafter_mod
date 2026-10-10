package laseta.bac.item;

import java.util.List;

import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.upgrade.TranslocationMode;
import laseta.bac.upgrade.UpgradeInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upgrade that makes the crafter's input (PULL) or output (PUSH) faces work from a linked Translocator.
 * Use it on a Translocator to link the held stack; craft it alone to clear the link. Sneak-use it on a crafter to
 * install it.
 * The basic version only works in the crafter's own dimension, the dimensional one anywhere.
 */
public class TranslocationUpgradeItem extends Item {
    private final TranslocationMode mode;
    private final boolean dimensional;

    public TranslocationUpgradeItem(TranslocationMode mode, boolean dimensional, Properties properties) {
        super(properties);
        this.mode = mode;
        this.dimensional = dimensional;
    }

    public TranslocationMode getMode() {
        return mode;
    }

    public boolean isDimensional() {
        return dimensional;
    }

    @Nullable
    public static GlobalPos getLink(ItemStack stack) {
        return stack.get(ModDataComponents.TRANSLOCATOR_LINK.get());
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        if (installed != InteractionResult.PASS) {
            return installed;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(ModBlocks.TRANSLOCATOR.get())) {
            return super.useOn(context);
        }
        if (!level.isClientSide) {
            link(context.getItemInHand(), context.getPlayer(), GlobalPos.of(level.dimension(), pos));
            level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Links the whole held stack in place (also in creative mode), replacing any previous link. */
    private static void link(ItemStack held, @Nullable Player player, GlobalPos link) {
        held.set(ModDataComponents.TRANSLOCATOR_LINK.get(), link);
        if (player != null) {
            BlockPos pos = link.pos();
            player.displayClientMessage(Component.translatable("item.better_auto_crafter.translocation_upgrade.linked_message",
                    pos.getX(), pos.getY(), pos.getZ()), true);
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return getLink(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        String key = "item.better_auto_crafter.translocation_upgrade.";
        tooltip.add(Component.translatable(key + mode.getSerializedName() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key + (dimensional ? "any_dimension" : "same_dimension"))
                .withStyle(dimensional ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
        GlobalPos link = getLink(stack);
        if (link == null) {
            tooltip.add(Component.translatable(key + "not_linked").withStyle(ChatFormatting.YELLOW));
            return;
        }
        BlockPos pos = link.pos();
        tooltip.add(Component.translatable(key + "linked", pos.getX(), pos.getY(), pos.getZ(),
                link.dimension().location().toString()).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable(key + "reset_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

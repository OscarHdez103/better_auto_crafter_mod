package laseta.bac.item;

import java.util.List;

import laseta.bac.registry.ModBlocks;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.upgrade.TranslocationMode;
import laseta.bac.upgrade.TranslocationUpgrade;
import laseta.bac.upgrade.UpgradeInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Upgrade that makes a machine's input (PULL) or output (PUSH) faces work from a linked Translocator.
 * Use it on a Translocator to link the held stack; sneak-use it at nothing (or craft it alone) to clear the link.
 * Sneak-use it on a machine to install it.
 * The basic version only works in the machine's own dimension, the dimensional one anywhere.
 */
public class TranslocationUpgradeItem extends LinkingUpgradeItem implements TranslocationUpgrade {
    private static final String LANG = "item.better_auto_crafter.translocation_upgrade.";

    private final TranslocationMode mode;
    private final boolean dimensional;

    public TranslocationUpgradeItem(TranslocationMode mode, boolean dimensional, Properties properties) {
        super(ModDataComponents.TRANSLOCATOR_LINK, LANG, properties);
        this.mode = mode;
        this.dimensional = dimensional;
    }

    @Override
    public TranslocationMode getMode() {
        return mode;
    }

    @Override
    public boolean isDimensional() {
        return dimensional;
    }

    @Override
    public int getLinkColor() {
        return mode.getColor();
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
            link(context.getItemInHand(), context.getPlayer(), GlobalPos.of(level.dimension(), pos),
                    Component.translatable(LANG + "linked_message", pos.getX(), pos.getY(), pos.getZ()));
            level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(LANG + mode.getSerializedName() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LANG + (dimensional ? "any_dimension" : "same_dimension"))
                .withStyle(dimensional ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
        appendLinkText(stack, tooltip, Component.translatable(LANG + "not_linked"));
    }
}

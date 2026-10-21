package laseta.bac.item;

import java.util.ArrayList;
import java.util.List;

import laseta.bac.CommonConfig;
import laseta.bac.registry.ModDataComponents;
import laseta.bac.upgrade.LinkedFace;
import laseta.bac.upgrade.TranslocationMode;
import laseta.bac.upgrade.TranslocationUpgrade;
import laseta.bac.upgrade.UpgradeInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upgrade that replaces a machine's input (PULL) or output (PUSH) faces with blocks linked directly, up to
 * {@link #getMaxTargets()} of them (server config): the machine pulls from / pushes into every one, which would otherwise take a
 * Translocator and an upgrade each. The side configuration's input (or output) faces are not used while it is installed.
 *
 * <p>Use it on a face of a block with an inventory to link that face (the machine then sees the block from that side,
 * like a hopper there would); use it on a linked face again to unlink it. Sneak-use at nothing (or craft it alone)
 * clears every link; sneak-use it on a machine to install it. The basic version links blocks of one dimension and
 * works in the machine's own dimension, the dimensional one anywhere.</p>
 */
public class MultiTranslocationUpgradeItem extends LinkingUpgradeItem implements TranslocationUpgrade {
    private static final String LANG = "item.better_auto_crafter.multi_translocation_upgrade.";
    /** Linked blocks listed in the tooltip (with Shift held); the rest are only counted. */
    private static final int TOOLTIP_TARGETS = 5;

    private final TranslocationMode mode;
    private final boolean dimensional;

    public MultiTranslocationUpgradeItem(TranslocationMode mode, boolean dimensional, Properties properties) {
        super(ModDataComponents.TRANSLOCATION_TARGETS, LANG, properties);
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

    /** How many faces one upgrade can link, from the server config. Machines only use the first ones up to this many. */
    public static int getMaxTargets() {
        return CommonConfig.multiTranslocationMaxLinks();
    }

    /** The linked blocks and faces, in link order (the order the machine uses them in). */
    public static List<LinkedFace> getTargets(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TRANSLOCATION_TARGETS.get(), List.of());
    }

    @Override
    public List<GlobalPos> getLinkedBlocks(ItemStack stack) {
        return getTargets(stack).stream().map(LinkedFace::pos).distinct().toList();
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        if (installed != InteractionResult.PASS) {
            return installed;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();
        Player player = context.getPlayer();
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) == null) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable(LANG + "cant_link"), true);
            }
            return super.useOn(context);
        }
        if (!level.isClientSide) {
            toggle(context.getItemInHand(), player, new LinkedFace(GlobalPos.of(level.dimension(), pos), side),
                    level.getBlockState(pos).getBlock().getName());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Links the face to the whole held stack, or unlinks it if it already is. Server side. */
    private void toggle(ItemStack held, @Nullable Player player, LinkedFace target, Component blockName) {
        List<LinkedFace> targets = new ArrayList<>(getTargets(held));
        boolean unlinked = targets.remove(target);
        if (!unlinked) {
            Component refusal = null;
            if (targets.size() >= getMaxTargets()) {
                refusal = Component.translatable(LANG + "full", getMaxTargets());
            } else if (!dimensional && !targets.isEmpty() && !targets.getFirst().pos().dimension().equals(target.pos().dimension())) {
                refusal = Component.translatable(LANG + "other_dimension");
            }
            if (refusal != null) {
                if (player != null) {
                    player.displayClientMessage(refusal, true);
                }
                return;
            }
            targets.add(target);
        }
        if (targets.isEmpty()) {
            clearLinks(held);
        } else {
            held.set(ModDataComponents.TRANSLOCATION_TARGETS.get(), List.copyOf(targets));
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(LANG + (unlinked ? "unlinked_message" : "linked_message"),
                    describe(target, blockName), targets.size(), getMaxTargets()), true);
            player.level().playSound(null, target.pos().pos(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS,
                    1.0F, unlinked ? 0.6F : 1.0F);
        }
    }

    /** "Chest (Up) at 1, 2, 3". */
    private static MutableComponent describe(LinkedFace target, Component blockName) {
        BlockPos pos = target.pos().pos();
        return Component.translatable(LANG + "target", blockName, sideName(target.side()), pos.getX(), pos.getY(), pos.getZ());
    }

    private static Component sideName(Direction side) {
        return Component.translatable("gui.better_auto_crafter.direction." + side.getSerializedName());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        String mode = this.mode.getSerializedName();
        tooltip.add(Component.translatable(LANG + mode + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LANG + mode + ".replaces_faces").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.better_auto_crafter.translocation_upgrade." + (dimensional ? "any_dimension" : "same_dimension"))
                .withStyle(dimensional ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));

        List<LinkedFace> targets = getTargets(stack);
        if (targets.isEmpty()) {
            tooltip.add(Component.translatable(LANG + "not_linked").withStyle(ChatFormatting.YELLOW));
            return;
        }
        // Over the limit (it was lowered in the config after linking): the extra links are kept but not used.
        MutableComponent count = Component.translatable(LANG + "linked_count", targets.size(), getMaxTargets())
                .withStyle(targets.size() > getMaxTargets() ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        if (!flag.hasShiftDown()) {
            tooltip.add(count.append(Component.translatable(LANG + "shift_hint").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        tooltip.add(count);
        for (int i = 0; i < Math.min(targets.size(), TOOLTIP_TARGETS); i++) {
            LinkedFace target = targets.get(i);
            BlockPos pos = target.pos().pos();
            MutableComponent line = Component.translatable(LANG + "target_line", sideName(target.side()), pos.getX(), pos.getY(), pos.getZ());
            if (dimensional) {
                line.append(Component.translatable(LANG + "target_dimension", target.pos().dimension().location().getPath()));
            }
            tooltip.add(line.withStyle(ChatFormatting.DARK_GREEN));
        }
        if (targets.size() > TOOLTIP_TARGETS) {
            tooltip.add(Component.translatable(LANG + "more", targets.size() - TOOLTIP_TARGETS).withStyle(ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable(LANG + "unlink_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.better_auto_crafter.linking_upgrade.reset_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

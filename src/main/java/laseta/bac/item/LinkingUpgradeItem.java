package laseta.bac.item;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An upgrade that is linked to blocks somewhere, stored in a data component: one dimension + position for most
 * upgrades, a list of them for the Multi Translocation Upgrades.
 * The whole held stack is linked at once; sneak-use at nothing (or crafting it alone) clears the link.
 * While held, the linked blocks are outlined in {@link #getLinkColor()}.
 */
public abstract class LinkingUpgradeItem extends Item {
    private final Supplier<? extends DataComponentType<?>> linkComponent;
    /** Translation key prefix shared by the item's messages and tooltip lines, e.g. "item.mod.translocation_upgrade.". */
    private final String langPrefix;

    protected LinkingUpgradeItem(Supplier<? extends DataComponentType<?>> linkComponent, String langPrefix, Properties properties) {
        super(properties);
        this.linkComponent = linkComponent;
        this.langPrefix = langPrefix;
    }

    /** RGB color of the outline drawn around the linked block while the upgrade is held. */
    public abstract int getLinkColor();

    /** The single block an upgrade is linked to, or null if it isn't linked (or links a list of blocks). */
    @Nullable
    public static GlobalPos getLink(ItemStack stack) {
        return stack.getItem() instanceof LinkingUpgradeItem item && stack.get(item.linkComponent.get()) instanceof GlobalPos link
                ? link : null;
    }

    public static boolean isLinked(ItemStack stack) {
        return stack.getItem() instanceof LinkingUpgradeItem item && stack.has(item.linkComponent.get());
    }

    /** Removes every link from the stack. */
    public void clearLinks(ItemStack stack) {
        stack.remove(linkComponent.get());
    }

    /** Every block the stack is linked to, outlined while it is held. */
    public List<GlobalPos> getLinkedBlocks(ItemStack stack) {
        GlobalPos link = getLink(stack);
        return link == null ? List.of() : List.of(link);
    }

    /**
     * Links the whole held stack in place (also in creative mode), replacing any previous link. Server side.
     * Only for upgrades whose link component holds a single {@link GlobalPos}.
     */
    @SuppressWarnings("unchecked")
    protected void link(ItemStack held, @Nullable Player player, GlobalPos link, Component linkedMessage) {
        held.set((DataComponentType<GlobalPos>) linkComponent.get(), link);
        if (player != null) {
            player.displayClientMessage(linkedMessage, true);
        }
    }

    /** Sneak-use while not looking at any block clears the link of the whole held stack. */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()
                || !isLinked(held)
                || getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() != HitResult.Type.MISS) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            clearLinks(held);
            player.displayClientMessage(Component.translatable(langPrefix + "cleared_message"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isLinked(stack) || super.isFoil(stack);
    }

    /** Adds "Linked: x, y, z (dimension)" and how to clear it, or the given hint when not linked. */
    protected void appendLinkText(ItemStack stack, List<Component> tooltip, Component notLinkedHint) {
        GlobalPos link = getLink(stack);
        if (link == null) {
            tooltip.add(notLinkedHint.copy().withStyle(ChatFormatting.YELLOW));
            return;
        }
        BlockPos pos = link.pos();
        tooltip.add(Component.translatable("item.better_auto_crafter.linking_upgrade.linked", pos.getX(), pos.getY(), pos.getZ(),
                link.dimension().location().toString()).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.better_auto_crafter.linking_upgrade.reset_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

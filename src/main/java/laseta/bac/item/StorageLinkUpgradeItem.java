package laseta.bac.item;

import java.util.List;

import laseta.bac.registry.ModDataComponents;
import laseta.bac.stock.StockReader;
import laseta.bac.upgrade.UpgradeInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Upgrade that makes the stocking upgrades count the result in a linked storage instead of in each output inventory.
 * Useful when the outputs feed a storage network (e.g. through an interface) whose contents the crafter can't see.
 *
 * <p>What it can be linked to and how it reads the stock comes from its {@link StockReader}: the basic upgrade
 * reads any inventory, the mod-specific ones a storage network through its controller.</p>
 */
public class StorageLinkUpgradeItem extends LinkingUpgradeItem {
    private static final String LANG = "item.better_auto_crafter.storage_link_upgrade.";

    private final StockReader reader;
    private final int linkColor;

    public StorageLinkUpgradeItem(StockReader reader, int linkColor, Properties properties) {
        super(ModDataComponents.STORAGE_LINK, LANG, properties);
        this.reader = reader;
        this.linkColor = linkColor;
    }

    public StockReader getReader() {
        return reader;
    }

    @Override
    public int getLinkColor() {
        return linkColor;
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        if (installed != InteractionResult.PASS) {
            return installed;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!reader.canLink(level, pos)) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable(LANG + "cant_link", reader.getTargetDescription()), true);
            }
            return super.useOn(context);
        }
        if (!level.isClientSide) {
            link(context.getItemInHand(), player, GlobalPos.of(level.dimension(), pos),
                    Component.translatable(LANG + "linked_message", level.getBlockState(pos).getBlock().getName(),
                            pos.getX(), pos.getY(), pos.getZ()));
            level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        appendLinkText(stack, tooltip, Component.translatable(LANG + "not_linked", reader.getTargetDescription()));
    }
}

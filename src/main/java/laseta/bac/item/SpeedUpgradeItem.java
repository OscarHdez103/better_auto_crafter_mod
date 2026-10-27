package laseta.bac.item;

import java.util.List;
import java.util.Locale;

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
 * Upgrade that makes each operation of a machine {@link #TICKS_PER_UPGRADE} ticks shorter, down to
 * {@link #MIN_TICKS}. A machine's slot only takes as many as can still make it faster, which depends on its base speed.
 */
public class SpeedUpgradeItem extends Item {
    public static final int TICKS_PER_UPGRADE = 2;
    /** Shortest an operation gets: continuous redstone modes don't try more often anyway. */
    public static final int MIN_TICKS = 2;

    public SpeedUpgradeItem(Properties properties) {
        super(properties);
    }

    /** Ticks an operation takes with {@code count} speed upgrades. Never longer than the base speed. */
    public static int getOperationTicks(int baseSpeed, int count) {
        return Math.min(baseSpeed, Math.max(MIN_TICKS, baseSpeed - count * TICKS_PER_UPGRADE));
    }

    /** How many speed upgrades it takes to reach {@link #MIN_TICKS} from {@code baseSpeed}; more would do nothing. */
    public static int getUsefulCount(int baseSpeed) {
        return Math.max(0, (baseSpeed - MIN_TICKS + TICKS_PER_UPGRADE - 1) / TICKS_PER_UPGRADE);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.better_auto_crafter.speed_upgrade.desc").withStyle(ChatFormatting.GRAY));
        // The whole hovered stack, so a slot of speed upgrades shows what it saves.
        tooltip.add(Component.translatable("item.better_auto_crafter.speed_upgrade.effect",
                        seconds(stack.getCount() * TICKS_PER_UPGRADE), seconds(MIN_TICKS))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        InteractionResult installed = UpgradeInstaller.tryInstall(context);
        return installed != InteractionResult.PASS ? installed : super.useOn(context);
    }
}

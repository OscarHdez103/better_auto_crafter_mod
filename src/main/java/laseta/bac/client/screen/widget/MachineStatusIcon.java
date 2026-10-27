package laseta.bac.client.screen.widget;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.machine.AutoMachineMenu;
import laseta.bac.machine.MachineProblem;
import laseta.bac.machine.MachineState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Small status icon of a machine GUI: a lamp in the colour of the machine's {@link MachineState}, inside a square ring
 * that fills up clockwise during the cooldown after each operation, like a loading circle. Hovering it tells the state,
 * why it fails or what it waits for, the time left and the redstone signal. Every machine screen has one.
 *
 * <p>The ring's shape is the "ring" sprite's: this only decides which of its pixels are lit, by their angle around the
 * icon's center, so a resource pack can reshape it.</p>
 */
public class MachineStatusIcon {
    public static final int SIZE = 12;
    private static final ResourceLocation FRAME = sprite("frame");
    private static final ResourceLocation RING = sprite("ring");
    private static final Map<MachineState, ResourceLocation> LAMPS = new EnumMap<>(MachineState.class);
    /** Clockwise position of each pixel around the center: 0 at the top middle, up to 1 for a full turn. */
    private static final float[][] RING_ORDER = new float[SIZE][SIZE];
    private static final float TICKS_PER_SECOND = 20.0F;

    static {
        for (MachineState state : MachineState.values()) {
            LAMPS.put(state, sprite("lamp_" + state.getSerializedName()));
        }
        float center = SIZE / 2.0F;
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                double angle = Math.atan2(column + 0.5 - center, center - (row + 0.5));
                RING_ORDER[row][column] = (float) ((angle < 0 ? angle + Math.PI * 2 : angle) / (Math.PI * 2));
            }
        }
    }

    private final AutoMachineMenu menu;
    private final MachineText text;

    public MachineStatusIcon(AutoMachineMenu menu, MachineText text) {
        this.menu = menu;
        this.text = text;
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(BetterAutoCrafter.MODID, "container/machine_status/" + name);
    }

    public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.blitSprite(FRAME, x, y, SIZE, SIZE);
        float progress = cooldownProgress(menu, partialTick);
        if (progress > 0) {
            renderRing(graphics, x, y, progress);
        }
        graphics.blitSprite(LAMPS.get(menu.getState()), x, y, SIZE, SIZE);
    }

    /** How much of the menu's machine's cooldown has passed, 0 to 1, smoothed between ticks; 0 when not cooling down. */
    public static float cooldownProgress(AutoMachineMenu menu, float partialTick) {
        int left = menu.getCooldownLeft();
        int total = menu.getCooldownTotal();
        if (left <= 0 || total <= 0) {
            return 0;
        }
        return Mth.clamp((total - left + partialTick) / total, 0.0F, 1.0F);
    }

    /** Draws the ring's pixels whose position is below {@code progress}, as horizontal runs (at most two per row). */
    private static void renderRing(GuiGraphics graphics, int x, int y, float progress) {
        for (int row = 0; row < SIZE; row++) {
            int column = 0;
            while (column < SIZE) {
                if (RING_ORDER[row][column] >= progress) {
                    column++;
                    continue;
                }
                int start = column;
                while (column < SIZE && RING_ORDER[row][column] < progress) {
                    column++;
                }
                graphics.blitSprite(RING, SIZE, SIZE, start, row, x + start, y + row, column - start, 1);
            }
        }
    }

    public static boolean isMouseOver(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    /** The state, what explains it, and the redstone mode and signal. */
    public List<Component> getTooltip() {
        MachineState state = menu.getState();
        Component redstone = Component.translatable("gui.better_auto_crafter.status.redstone",
                menu.getRedstoneMode().getDisplayName(),
                Component.translatable("gui.better_auto_crafter.status." + (menu.hasSignal() ? "powered" : "unpowered")));
        return List.of(text.get("status." + state.getSerializedName()).withStyle(color(state)),
                detail(state).copy().withStyle(ChatFormatting.GRAY),
                redstone.copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    private Component detail(MachineState state) {
        MachineProblem problem = menu.getProblem();
        return switch (state) {
            case OFF -> text.get("status.off.desc");
            case WORKING -> menu.getCooldownLeft() > 0
                    ? text.get("status.cooldown", String.format(Locale.ROOT, "%.1f", menu.getCooldownLeft() / TICKS_PER_SECOND))
                    : text.get("status.working.desc");
            case WAITING, FAILED -> problem != null
                    ? text.get("problem." + problem.getSerializedName())
                    : text.get("status.waiting." + menu.getRedstoneMode().getSerializedName());
        };
    }

    private static ChatFormatting color(MachineState state) {
        return switch (state) {
            case OFF -> ChatFormatting.GRAY;
            case WAITING -> ChatFormatting.GOLD;
            case WORKING -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
        };
    }
}

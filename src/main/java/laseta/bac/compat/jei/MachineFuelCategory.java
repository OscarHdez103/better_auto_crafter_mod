package laseta.bac.compat.jei;

import java.util.function.BiConsumer;

import laseta.bac.BetterAutoCrafter;
import laseta.bac.compat.BrewerFuelBar;
import laseta.bac.compat.MachineFuel;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * JEI category of a machine's fuels, laid out like JEI's own fuel category: the fuel's slot under the machine's fuel
 * indicator (the smelter's flame, the brewer's blaze bar), and next to them the fuel it gives.
 */
public class MachineFuelCategory extends AbstractRecipeCategory<MachineFuel> {
    public static final RecipeType<MachineFuel> SMELTER = RecipeType.create(BetterAutoCrafter.MODID, "smelter_fuel", MachineFuel.class);
    public static final RecipeType<MachineFuel> BREWER = RecipeType.create(BetterAutoCrafter.MODID, "brewer_fuel", MachineFuel.class);

    private static final int WIDTH = 120;
    private static final int HEIGHT = 34;
    private static final int SLOT_X = 2;
    private static final int SLOT_Y = 17;
    private static final int TEXT_X = 26;
    private static final int TEXT_COLOR = 0xFF808080;

    /** Adds the machine's fuel indicator, above the slot. */
    private final BiConsumer<IRecipeExtrasBuilder, MachineFuel> indicator;

    private MachineFuelCategory(RecipeType<MachineFuel> type, String titleKey, IDrawable icon,
                                BiConsumer<IRecipeExtrasBuilder, MachineFuel> indicator) {
        super(type, Component.translatable(titleKey), icon, WIDTH, HEIGHT);
        this.indicator = indicator;
    }

    public static MachineFuelCategory smelter(IGuiHelper guiHelper) {
        // Like JEI's furnace fuels, the flame burns for as long as the fuel does.
        return new MachineFuelCategory(SMELTER, "gui.better_auto_crafter.jei.smelter_fuel", guiHelper.createDrawableItemLike(Items.COAL),
                (builder, fuel) -> builder.addAnimatedRecipeFlameWidget(fuel.fuel()).setPosition(SLOT_X + 1, 1));
    }

    public static MachineFuelCategory brewer(IGuiHelper guiHelper) {
        return new MachineFuelCategory(BREWER, "gui.better_auto_crafter.jei.brewer_fuel", guiHelper.createDrawableItemLike(Items.BLAZE_POWDER),
                (builder, fuel) -> builder.addDrawableWidget(new IDrawable() {
                    @Override
                    public int getWidth() {
                        return BrewerFuelBar.WIDTH;
                    }

                    @Override
                    public int getHeight() {
                        return BrewerFuelBar.HEIGHT;
                    }

                    @Override
                    public void draw(@NotNull GuiGraphics graphics, int xOffset, int yOffset) {
                        BrewerFuelBar.render(graphics, xOffset, yOffset);
                    }
                }).setPosition(SLOT_X - 2, 6));
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, MachineFuel fuel, @NotNull IFocusGroup focuses) {
        builder.addInputSlot(SLOT_X, SLOT_Y).setStandardSlotBackground().addItemStack(fuel.stack());
    }

    @Override
    public void createRecipeExtras(@NotNull IRecipeExtrasBuilder builder, @NotNull MachineFuel fuel, @NotNull IFocusGroup focuses) {
        indicator.accept(builder, fuel);
        builder.addText(Component.translatable("gui.better_auto_crafter.jei.gives_fuel", fuel.fuel()), WIDTH - TEXT_X, HEIGHT)
                .setPosition(TEXT_X, 0)
                .setTextAlignment(VerticalAlignment.CENTER)
                .setColor(TEXT_COLOR);
    }
}

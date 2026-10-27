package laseta.bac.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** Smelter upgrade: only the fuel it is set to is used, instead of any fuel on the fuel faces. */
public class FuelFilterUpgradeItem extends ItemSettingUpgradeItem {
    public FuelFilterUpgradeItem(Properties properties) {
        super(properties);
    }

    /** Only fuels: anything else would stop the smelter for good. */
    @Override
    public boolean canBeSetTo(ItemStack candidate) {
        return !candidate.isEmpty() && candidate.getBurnTime(RecipeType.SMELTING) > 0;
    }

    @Override
    protected String descriptionKey() {
        return "item.better_auto_crafter.fuel_filter_upgrade.desc";
    }
}

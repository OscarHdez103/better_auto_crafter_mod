package laseta.bac.item;

import net.minecraft.world.item.ItemStack;

/** Brewer upgrade: brews start from the item it is set to (e.g. Awkward Potions) instead of water bottles. */
public class PotionStarterUpgradeItem extends ItemSettingUpgradeItem {
    public PotionStarterUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canBeSetTo(ItemStack candidate) {
        return !candidate.isEmpty() && !(candidate.getItem() instanceof ItemSettingUpgradeItem);
    }

    @Override
    protected String descriptionKey() {
        return "item.better_auto_crafter.potion_starter_upgrade.desc";
    }
}

package laseta.bac.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

/** A plain crafting ingredient that says what it is for: its tooltip is "&lt;its translation key&gt;.desc". */
public class CraftingComponentItem extends Item {
    public CraftingComponentItem(Properties properties) {
        super(properties);
    }
}

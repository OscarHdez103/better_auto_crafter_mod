package laseta.bac.compat;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import laseta.bac.block.entity.BetterAutoBrewerBlockEntity;
import laseta.bac.block.entity.BetterAutoSmelterBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * An item a machine burns and the fuel it gives, for the recipe viewers' fuel pages. The lists come from the machines'
 * own fuel values, so every fuel shows up: modded ones, data pack ones and tagged ones alike.
 */
public record MachineFuel(ItemStack stack, int fuel) {
    /** Everything the smelter burns, the longest burning first. */
    public static List<MachineFuel> smelterFuels() {
        return fuels(BetterAutoSmelterBlockEntity::fuelValue);
    }

    /** Everything the brewer burns, the longest burning first. */
    public static List<MachineFuel> brewerFuels() {
        return fuels(BetterAutoBrewerBlockEntity::fuelValue);
    }

    private static List<MachineFuel> fuels(ToIntFunction<ItemStack> fuelValue) {
        return BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new)
                .filter(stack -> !stack.isEmpty())
                .map(stack -> new MachineFuel(stack, fuelValue.applyAsInt(stack)))
                .filter(fuel -> fuel.fuel() > 0)
                .sorted(Comparator.comparingInt(MachineFuel::fuel).reversed())
                .toList();
    }
}

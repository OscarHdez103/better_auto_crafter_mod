package laseta.bac.material;

import java.util.List;

import laseta.bac.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/** The mod's {@link SharedMaterial}s. */
public final class Materials {
    /** Made in the Better Auto Smelter from iron; goes into the speed and coprocessing upgrades. */
    public static final SharedMaterial STEEL = new SharedMaterial(commonItemTag("ingots/steel"), ModItems.STEEL_INGOT,
            List.of(ModItems.STEEL_INGOT, ModItems.STEEL_NUGGET, ModItems.STEEL_BLOCK));

    public static final List<SharedMaterial> ALL = List.of(STEEL);

    private Materials() {
    }

    /** A {@code c:} (common, shared by all mods) item tag. */
    public static TagKey<Item> commonItemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    /** Whether {@code item} is one of this mod's items of a material another mod provides instead. */
    public static boolean isHidden(ItemLike item, HolderLookup.Provider registries) {
        return ALL.stream().anyMatch(material -> material.isHidden(item, registries));
    }
}

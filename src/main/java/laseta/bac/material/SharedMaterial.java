package laseta.bac.material;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import laseta.bac.BetterAutoCrafter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * A material many mods add, like steel. This mod has its own items for it, but only uses them while no other mod has
 * the material's main item, so a pack with another mod's steel has one steel instead of two:
 * <ul>
 *     <li>this mod's items are left out of the creative tab, JEI and EMI ({@link #isHidden});</li>
 *     <li>their recipes aren't loaded (they have a {@link NoOtherModInTagCondition} on the main tag);</li>
 *     <li>what this mod makes of the material is the other mod's item ({@link #mainItem}, and the
 *     {@code result_tag} of the smelter's recipes).</li>
 * </ul>
 *
 * <p>Other mods' items are found through the main item's common tag ({@code c:ingots/steel}), which this mod's main
 * item is in too: this mod's recipes take any mod's steel, other mods' recipes take this mod's, and steel made before
 * another steel mod was added still works.</p>
 *
 * @param mainTag the main item's common tag
 * @param main    this mod's main item (the ingot)
 * @param family  all of this mod's items of the material (ingot, nugget, block, ...), used or hidden together
 */
public record SharedMaterial(TagKey<Item> mainTag, Supplier<? extends ItemLike> main, List<Supplier<? extends ItemLike>> family) {
    /** The first item in {@code tagged} that isn't this mod's, in the tag's order (so a pack's tag order decides). */
    public static Optional<Holder<Item>> otherModItem(Iterable<Holder<Item>> tagged) {
        for (Holder<Item> holder : tagged) {
            Optional<ResourceKey<Item>> key = holder.unwrapKey();
            if (key.isPresent() && !key.get().location().getNamespace().equals(BetterAutoCrafter.MODID)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    /** The first item of another mod in {@code tag}, with the registries' current tags. */
    public static Optional<Holder<Item>> otherModItem(HolderLookup.Provider registries, TagKey<Item> tag) {
        return registries.lookup(Registries.ITEM)
                .flatMap(items -> items.get(tag))
                .flatMap(SharedMaterial::otherModItem);
    }

    /** Whether another mod has the main item: then this mod's items of the material aren't used. */
    public boolean isProvidedByOtherMod(HolderLookup.Provider registries) {
        return otherModItem(registries, mainTag).isPresent();
    }

    /** {@code count} of the main item this mod makes: another mod's if there is one, this mod's otherwise. */
    public ItemStack mainItem(HolderLookup.Provider registries, int count) {
        return otherModItem(registries, mainTag)
                .map(holder -> new ItemStack(holder, count))
                .orElseGet(() -> new ItemStack(main.get(), count));
    }

    public boolean isInFamily(ItemLike item) {
        return family.stream().anyMatch(member -> member.get().asItem() == item.asItem());
    }

    /** Whether {@code item} is one of this mod's items of the material while another mod's are used instead. */
    public boolean isHidden(ItemLike item, HolderLookup.Provider registries) {
        return isInFamily(item) && isProvidedByOtherMod(registries);
    }
}

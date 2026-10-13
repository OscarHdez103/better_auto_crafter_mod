package laseta.bac.compat;

import java.util.List;
import java.util.function.Supplier;

import laseta.bac.compat.ae2.Ae2StockReader;
import laseta.bac.compat.refinedstorage.RefinedStorageStockReader;
import laseta.bac.compat.tomsstorage.TomsStorageStockReader;
import laseta.bac.stock.StockReader;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.ModList;

/**
 * The mod-specific Storage Link Upgrades. Each one only exists (item, recipe, creative tab entry) when its mod is
 * installed; without the mod its reader class is never loaded, so the mod's classes are never touched.
 *
 * <p>To support another storage mod:</p>
 * <ol>
 *     <li>Write a {@link StockReader} for it in {@code compat/<mod>} (add its API as {@code compileOnly} if needed).</li>
 *     <li>Add an entry below.</li>
 *     <li>Add the item's model, texture and translations, and a recipe (plus its unlock advancement) with a
 *     {@code neoforge:mod_loaded} condition.</li>
 * </ol>
 */
public final class StorageLinkIntegrations {
    /**
     * @param modId    the storage mod; the upgrade is only registered when it is loaded
     * @param itemName the upgrade's registry name
     * @param reader   creates the reader. Must stay a lambda ({@code () -> new X()}), not a method reference: a method
     *                 reference would load the reader class (and so the mod's classes) when this list is built
     * @param color    RGB outline color of the linked block while the upgrade is held
     */
    public record Integration(String modId, String itemName, Supplier<StockReader> reader, int color, Rarity rarity) {
        public boolean isAvailable() {
            return ModList.get().isLoaded(modId);
        }
    }

    @SuppressWarnings("Convert2MethodRef")
    public static final List<Integration> ALL = List.of(
            new Integration("ae2", "me_storage_link_upgrade", () -> new Ae2StockReader(), 0x9F6BFF, Rarity.UNCOMMON),
            new Integration("refinedstorage", "refined_storage_link_upgrade", () -> new RefinedStorageStockReader(), 0x5FB8FF, Rarity.UNCOMMON),
            new Integration("toms_storage", "toms_storage_link_upgrade", () -> new TomsStorageStockReader(), 0xE0A040, Rarity.UNCOMMON));

    private StorageLinkIntegrations() {
    }
}

package laseta.bac.material;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.NotNull;

/**
 * Data condition: true while no other mod has an item in the tag (only this mod's, or none). Recipes and advancements of
 * a {@link SharedMaterial}'s items use it, so they are only loaded while this mod's items are the ones used:
 *
 * <pre>{@code
 * "neoforge:conditions": [
 *   { "type": "better_auto_crafter:no_other_mod_in_tag", "tag": "c:ingots/steel" }
 * ]
 * }</pre>
 */
public record NoOtherModInTagCondition(TagKey<Item> tag) implements ICondition {
    public static final MapCodec<NoOtherModInTagCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(NoOtherModInTagCondition::tag)
    ).apply(instance, NoOtherModInTagCondition::new));

    @Override
    public boolean test(@NotNull IContext context) {
        return SharedMaterial.otherModItem(context.getTag(tag)).isEmpty();
    }

    @Override
    public @NotNull MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public @NotNull String toString() {
        return "no_other_mod_in_tag(\"" + tag.location() + "\")";
    }
}

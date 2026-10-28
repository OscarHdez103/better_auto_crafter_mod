package laseta.bac.machine;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The recipes a machine's template could make, when more than one does (mods often add recipes for the same items), and
 * which of them it makes. The player picks another in the GUI, like with Polymorph.
 *
 * @param choices in the machine's default order: the first is what it makes unless told otherwise
 * @param chosen  index of the one it makes, -1 if none
 */
public record RecipeChoices(List<Choice> choices, int chosen) {
    /** Most choices offered, which also caps the packet size. */
    public static final int MAX = 32;
    public static final RecipeChoices NONE = new RecipeChoices(List.of(), -1);

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoices> STREAM_CODEC = StreamCodec.composite(
            Choice.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), RecipeChoices::choices,
            ByteBufCodecs.VAR_INT, RecipeChoices::chosen,
            RecipeChoices::new);

    /**
     * One recipe the template could make.
     *
     * @param result what it makes, as shown
     * @param group  a kind of recipe the machine tells apart (e.g. the smelter's furnace types), 0 if it doesn't
     */
    public record Choice(ItemStack result, int group) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Choice::result,
                ByteBufCodecs.VAR_INT, Choice::group,
                Choice::new);
    }

    public RecipeChoices {
        choices = List.copyOf(choices.subList(0, Math.min(MAX, choices.size())));
        chosen = chosen >= 0 && chosen < choices.size() ? chosen : -1;
    }

    /** The chosen recipe, or null. */
    public Choice chosenChoice() {
        return chosen < 0 ? null : choices.get(chosen);
    }

    /** Whether this says the same as {@code other} (item stacks don't compare by content on their own). */
    public boolean sameAs(RecipeChoices other) {
        if (chosen != other.chosen || choices.size() != other.choices.size()) {
            return false;
        }
        for (int i = 0; i < choices.size(); i++) {
            Choice a = choices.get(i);
            Choice b = other.choices.get(i);
            if (a.group() != b.group() || !ItemStack.matches(a.result(), b.result())) {
                return false;
            }
        }
        return true;
    }
}

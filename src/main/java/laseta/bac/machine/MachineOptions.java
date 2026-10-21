package laseta.bac.machine;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.ContainerData;

/**
 * The current values of a machine's {@link MachineOption}s. Owned by a block entity; {@code onChanged} is called
 * whenever a value changes. Exposed to the menu as one data slot per option holding the value's ordinal.
 */
public class MachineOptions {
    /** Most values one option can have (the menu reserves this many button ids per option). */
    public static final int MAX_VALUES = 8;

    private final List<MachineOption<?>> definitions;
    private final int[] values;
    private final Runnable onChanged;

    public MachineOptions(List<MachineOption<?>> definitions, Runnable onChanged) {
        this.definitions = List.copyOf(definitions);
        this.values = new int[definitions.size()];
        this.onChanged = onChanged;
        for (int i = 0; i < values.length; i++) {
            values[i] = definitions.get(i).defaultValue().ordinal();
        }
    }

    public List<MachineOption<?>> definitions() {
        return definitions;
    }

    public <T extends Enum<T> & StringRepresentable> T get(MachineOption<T> option) {
        return option.byId(values[indexOf(option)]);
    }

    public <T extends Enum<T> & StringRepresentable> void set(MachineOption<T> option, T value) {
        setId(indexOf(option), value.ordinal());
    }

    private void setId(int index, int id) {
        int valid = definitions.get(index).byId(id).ordinal();
        if (values[index] != valid) {
            values[index] = valid;
            onChanged.run();
        }
    }

    private int indexOf(MachineOption<?> option) {
        int index = definitions.indexOf(option);
        if (index < 0) {
            throw new IllegalArgumentException("This machine has no option " + option.name());
        }
        return index;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < values.length; i++) {
            MachineOption<?> option = definitions.get(i);
            tag.putString(option.name(), option.byId(values[i]).getSerializedName());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        // Values set directly: loading must not mark the block entity as changed.
        for (int i = 0; i < values.length; i++) {
            MachineOption<?> option = definitions.get(i);
            values[i] = option.byName(tag.getString(option.name())).ordinal();
        }
    }

    public ContainerData asContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return values[index];
            }

            @Override
            public void set(int index, int value) {
                setId(index, value);
            }

            @Override
            public int getCount() {
                return values.length;
            }
        };
    }
}

package laseta.bac.machine;

import java.util.List;

import net.minecraft.util.StringRepresentable;

/**
 * A setting only some machines have, picked from a fixed list of values (an enum). A machine lists its options in its
 * block entity's constructor; they are saved with the block, synced to its menu and shown in the machine's own tab,
 * one row per option. Compare {@link MachineSettings}, which every machine has.
 *
 * <p>The values' serialized names are saved, so values can be reordered or added later; an unknown name loads as the
 * default. GUI texts use the option's name, see the client's {@code MachineTab}.</p>
 */
public final class MachineOption<T extends Enum<T> & StringRepresentable> {
    private final String name;
    private final List<T> values;
    private final T defaultValue;

    private MachineOption(String name, Class<T> type, T defaultValue) {
        this.name = name;
        this.values = List.of(type.getEnumConstants());
        this.defaultValue = defaultValue;
        if (values.size() > MachineOptions.MAX_VALUES) {
            throw new IllegalArgumentException("Machine option " + name + " has more than " + MachineOptions.MAX_VALUES + " values");
        }
    }

    /** @param name saved under this key, and used in the GUI texts' keys */
    public static <T extends Enum<T> & StringRepresentable> MachineOption<T> of(String name, Class<T> type, T defaultValue) {
        return new MachineOption<>(name, type, defaultValue);
    }

    public String name() {
        return name;
    }

    public List<T> values() {
        return values;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** The value with this ordinal, or the default if there is none. */
    public T byId(int id) {
        return id >= 0 && id < values.size() ? values.get(id) : defaultValue;
    }

    /** The value with this serialized name, or the default if there is none. */
    public T byName(String serializedName) {
        for (T value : values) {
            if (value.getSerializedName().equals(serializedName)) {
                return value;
            }
        }
        return defaultValue;
    }
}

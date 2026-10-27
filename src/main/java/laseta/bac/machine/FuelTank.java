package laseta.bac.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;

/**
 * Fuel a machine has stored, in its own units (e.g. burn ticks, or brews). Machines only refuel when an operation needs
 * more than is stored, so the tank never fills up for nothing. Owned by a block entity; {@code onChanged} is called
 * whenever something that must be saved changes.
 *
 * <p>Fuels differ a lot (a stick is 100 ticks, a lava bucket 20,000, modded fuels even more), so the GUI shows the fuel
 * against what the tank held right after it last refuelled ({@link #getCapacity()}), like a vanilla furnace's flame
 * shows what is left of its current fuel item. That never overflows, whatever the fuel.</p>
 */
public class FuelTank {
    // Menu data slot indices: vanilla syncs data slots as 16-bit values, so each number is split in two.
    private static final int DATA_STORED = 0;
    private static final int DATA_CAPACITY = 2;
    public static final int DATA_COUNT = 4;

    private final Runnable onChanged;
    private int stored;
    /** What the tank held right after it last refuelled; the GUI's 100%. */
    private int capacity;

    public FuelTank(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public int getStored() {
        return stored;
    }

    public int getCapacity() {
        return Math.max(capacity, stored);
    }

    /** Adds fuel just taken from an inventory. */
    public void add(int amount) {
        if (amount <= 0) {
            return;
        }
        stored = (int) Math.min(Integer.MAX_VALUE, (long) stored + amount);
        capacity = stored;
        onChanged.run();
    }

    /** Uses up fuel; never below empty. */
    public void consume(int amount) {
        if (amount <= 0 || stored == 0) {
            return;
        }
        stored = Math.max(0, stored - amount);
        onChanged.run();
    }

    /** How much more an operation costing {@code cost} needs than is stored; 0 if there is enough. */
    public int missingFor(int cost) {
        return Math.max(0, cost - stored);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stored", stored);
        tag.putInt("capacity", capacity);
        return tag;
    }

    public void load(CompoundTag tag) {
        // Fields set directly: loading must not mark the block entity as changed.
        stored = Math.max(0, tag.getInt("stored"));
        capacity = Math.max(stored, tag.getInt("capacity"));
    }

    /** The stored fuel and the capacity as menu data, see {@link #stored(ContainerData)}. Read-only. */
    public ContainerData asContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int value = index < DATA_CAPACITY ? stored : getCapacity();
                return index % 2 == 0 ? value & 0xFFFF : value >>> 16;
            }

            @Override
            public void set(int index, int value) {
                // Read-only: changed by the machine's operations.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** The stored fuel from {@link #asContainerData()} as synced to a client. */
    public static int stored(ContainerData data) {
        return combine(data, DATA_STORED);
    }

    public static int capacity(ContainerData data) {
        return combine(data, DATA_CAPACITY);
    }

    private static int combine(ContainerData data, int index) {
        return (data.get(index + 1) & 0xFFFF) << 16 | data.get(index) & 0xFFFF;
    }
}

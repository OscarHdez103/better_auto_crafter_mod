package laseta.bac.sideconfig;

import java.util.Arrays;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;

/**
 * Stores a {@link SideMode} for every {@link RelativeSide} of a block.
 * Owned by a block entity; {@code onChanged} is called whenever a mode actually changes.
 */
public class SideConfiguration {
    private final SideMode[] modes = new SideMode[RelativeSide.COUNT];
    private final Runnable onChanged;

    public SideConfiguration(Runnable onChanged) {
        this.onChanged = onChanged;
        Arrays.fill(modes, SideMode.NONE);
    }

    public SideMode getMode(RelativeSide side) {
        return modes[side.ordinal()];
    }

    /** Mode of the face pointing towards {@code direction} in the world (useful for capabilities / item transfer). */
    public SideMode getMode(Direction facing, Direction direction) {
        return getMode(RelativeSide.fromDirection(facing, direction));
    }

    public void setMode(RelativeSide side, SideMode mode) {
        if (modes[side.ordinal()] != mode) {
            modes[side.ordinal()] = mode;
            onChanged.run();
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (RelativeSide side : RelativeSide.values()) {
            tag.putString(side.getSerializedName(), getMode(side).getSerializedName());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        // Written directly to the array: loading should not mark the block entity as changed.
        for (RelativeSide side : RelativeSide.values()) {
            modes[side.ordinal()] = SideMode.byName(tag.getString(side.getSerializedName()));
        }
    }

    /**
     * Exposes the modes as menu data slots (index = {@link RelativeSide#ordinal()}, value = {@link SideMode#ordinal()}),
     * so vanilla's container syncing keeps open screens up to date.
     */
    public ContainerData asContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return modes[index].ordinal();
            }

            @Override
            public void set(int index, int value) {
                setMode(RelativeSide.byId(index), SideMode.byId(value));
            }

            @Override
            public int getCount() {
                return RelativeSide.COUNT;
            }
        };
    }
}

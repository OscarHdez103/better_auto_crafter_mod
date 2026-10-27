package laseta.bac.machine;

import java.util.List;

import laseta.bac.sideconfig.RelativeSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.ContainerData;

/**
 * Which faces are on for each of a machine's {@link FaceChannel}s. Owned by a block entity; {@code onChanged} is called
 * whenever a face changes. Exposed to the menu as one data slot per channel holding a bit per {@link RelativeSide}.
 */
public class ChannelFaces {
    private final List<FaceChannel> channels;
    private final int[] masks;
    private final Runnable onChanged;

    public ChannelFaces(List<FaceChannel> channels, Runnable onChanged) {
        this.channels = List.copyOf(channels);
        this.masks = new int[channels.size()];
        this.onChanged = onChanged;
    }

    public List<FaceChannel> channels() {
        return channels;
    }

    public boolean isOn(FaceChannel channel, RelativeSide side) {
        return isOn(masks[indexOf(channel)], side);
    }

    public static boolean isOn(int mask, RelativeSide side) {
        return (mask & 1 << side.ordinal()) != 0;
    }

    public void set(FaceChannel channel, RelativeSide side, boolean on) {
        int index = indexOf(channel);
        setMask(index, on ? masks[index] | 1 << side.ordinal() : masks[index] & ~(1 << side.ordinal()));
    }

    private void setMask(int index, int mask) {
        int valid = mask & (1 << RelativeSide.COUNT) - 1;
        if (masks[index] != valid) {
            masks[index] = valid;
            onChanged.run();
        }
    }

    private int indexOf(FaceChannel channel) {
        int index = channels.indexOf(channel);
        if (index < 0) {
            throw new IllegalArgumentException("This machine has no channel " + channel.name());
        }
        return index;
    }

    /** Saved as the names of the faces that are on, so it doesn't depend on the order of {@link RelativeSide}. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < masks.length; i++) {
            ListTag sides = new ListTag();
            for (RelativeSide side : RelativeSide.values()) {
                if (isOn(masks[i], side)) {
                    sides.add(StringTag.valueOf(side.getSerializedName()));
                }
            }
            tag.put(channels.get(i).name(), sides);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        // Written directly: loading must not mark the block entity as changed.
        for (int i = 0; i < masks.length; i++) {
            int mask = 0;
            ListTag sides = tag.getList(channels.get(i).name(), Tag.TAG_STRING);
            for (int j = 0; j < sides.size(); j++) {
                for (RelativeSide side : RelativeSide.values()) {
                    if (side.getSerializedName().equals(sides.getString(j))) {
                        mask |= 1 << side.ordinal();
                    }
                }
            }
            masks[i] = mask;
        }
    }

    public ContainerData asContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return masks[index];
            }

            @Override
            public void set(int index, int value) {
                setMask(index, value);
            }

            @Override
            public int getCount() {
                return masks.length;
            }
        };
    }
}

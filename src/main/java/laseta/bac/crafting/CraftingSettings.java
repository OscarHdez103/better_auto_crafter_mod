package laseta.bac.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;

/**
 * Player-configurable settings for crafting from neighbouring inventories, plus the round robin positions.
 * Owned by a block entity; {@code onChanged} is called whenever something that must be saved changes.
 */
public class CraftingSettings {
    // Menu data slot indices (see asContainerData)
    public static final int DATA_MATCH_NBT = 0;
    public static final int DATA_INPUT_PRIORITY = 1;
    public static final int DATA_OUTPUT_PRIORITY = 2;
    public static final int DATA_REDSTONE_MODE = 3;
    public static final int DATA_COUNT = 4;

    private final Runnable onChanged;

    /** On: pulled items must have the same data components (NBT) as the grid items. Off: only the item must match. */
    private boolean matchNbt = true;
    private TransferPriority inputPriority = TransferPriority.ROUND_ROBIN;
    private TransferPriority outputPriority = TransferPriority.ROUND_ROBIN;
    private RedstoneMode redstoneMode = RedstoneMode.OFF;
    private int inputCursor;
    private int outputCursor;

    public CraftingSettings(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public boolean isMatchNbt() {
        return matchNbt;
    }

    public void setMatchNbt(boolean matchNbt) {
        if (this.matchNbt != matchNbt) {
            this.matchNbt = matchNbt;
            onChanged.run();
        }
    }

    public TransferPriority getInputPriority() {
        return inputPriority;
    }

    public void setInputPriority(TransferPriority inputPriority) {
        if (this.inputPriority != inputPriority) {
            this.inputPriority = inputPriority;
            onChanged.run();
        }
    }

    public TransferPriority getOutputPriority() {
        return outputPriority;
    }

    public void setOutputPriority(TransferPriority outputPriority) {
        if (this.outputPriority != outputPriority) {
            this.outputPriority = outputPriority;
            onChanged.run();
        }
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(RedstoneMode redstoneMode) {
        if (this.redstoneMode != redstoneMode) {
            this.redstoneMode = redstoneMode;
            onChanged.run();
        }
    }

    public int getInputCursor() {
        return inputCursor;
    }

    public int getOutputCursor() {
        return outputCursor;
    }

    /** Called after every successful craft so round robin starts at the next face. */
    public void advanceRoundRobin() {
        boolean changed = false;
        if (inputPriority == TransferPriority.ROUND_ROBIN) {
            inputCursor = (inputCursor + 1) & Integer.MAX_VALUE;
            changed = true;
        }
        if (outputPriority == TransferPriority.ROUND_ROBIN) {
            outputCursor = (outputCursor + 1) & Integer.MAX_VALUE;
            changed = true;
        }
        if (changed) {
            onChanged.run();
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("match_nbt", matchNbt);
        tag.putString("input_priority", inputPriority.getSerializedName());
        tag.putString("output_priority", outputPriority.getSerializedName());
        tag.putString("redstone_mode", redstoneMode.getSerializedName());
        tag.putInt("input_cursor", inputCursor);
        tag.putInt("output_cursor", outputCursor);
        return tag;
    }

    public void load(CompoundTag tag) {
        // Fields set directly: loading must not mark the block entity as changed.
        matchNbt = !tag.contains("match_nbt") || tag.getBoolean("match_nbt");
        inputPriority = TransferPriority.byName(tag.getString("input_priority"));
        outputPriority = TransferPriority.byName(tag.getString("output_priority"));
        // Blocks saved before this setting existed always crafted on a rising edge: keep that behaviour for them.
        // Brand new blocks start as OFF (the field default) and never get here without the key.
        redstoneMode = tag.contains("redstone_mode")
                ? RedstoneMode.byName(tag.getString("redstone_mode"))
                : (tag.isEmpty() ? RedstoneMode.OFF : RedstoneMode.ON_POWERED);
        inputCursor = Math.max(0, tag.getInt("input_cursor"));
        outputCursor = Math.max(0, tag.getInt("output_cursor"));
    }

    /** Exposes the player-facing settings as menu data slots so open screens stay in sync. */
    public ContainerData asContainerData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_MATCH_NBT -> matchNbt ? 1 : 0;
                    case DATA_INPUT_PRIORITY -> inputPriority.ordinal();
                    case DATA_OUTPUT_PRIORITY -> outputPriority.ordinal();
                    case DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case DATA_MATCH_NBT -> setMatchNbt(value != 0);
                    case DATA_INPUT_PRIORITY -> setInputPriority(TransferPriority.byId(value));
                    case DATA_OUTPUT_PRIORITY -> setOutputPriority(TransferPriority.byId(value));
                    case DATA_REDSTONE_MODE -> setRedstoneMode(RedstoneMode.byId(value));
                    default -> {
                    }
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }
}

package laseta.bac.machine;

import java.util.List;

/**
 * What only some machines have, all shown in the machine's own tab: options ({@link MachineOption}), face channels
 * ({@link FaceChannel}) and upgrade slots ({@link MachineUpgradeSlotType}), each in the order the tab shows them.
 * A machine defines its features once (statically), since its client-side menu needs them too.
 */
public record MachineFeatures(List<MachineOption<?>> options, List<FaceChannel> channels,
                              List<MachineUpgradeSlotType> upgradeSlots) {
    public static final MachineFeatures NONE = new MachineFeatures(List.of(), List.of(), List.of());

    public MachineFeatures {
        options = List.copyOf(options);
        channels = List.copyOf(channels);
        upgradeSlots = List.copyOf(upgradeSlots);
    }

    public static MachineFeatures ofOptions(List<MachineOption<?>> options) {
        return new MachineFeatures(options, List.of(), List.of());
    }

    /** Whether the machine has a tab of its own. */
    public boolean isEmpty() {
        return options.isEmpty() && channels.isEmpty() && upgradeSlots.isEmpty();
    }
}

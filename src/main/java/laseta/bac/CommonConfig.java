package laseta.bac;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config: {@code config/better_auto_crafter-common.toml}, one file for every world. It isn't sent to clients,
 * so anything both sides must agree on is sent along when needed (e.g. the mover's grid size, with its menu).
 */
public final class CommonConfig {
    /** Grid limits: up to the player inventory's width, and a height that still fits the GUI on small screens. */
    public static final int MAX_GRID_WIDTH = 6;
    public static final int MAX_GRID_HEIGHT = 6;
    /** Tabs of the Better Auto Hub, once all of them are unlocked. */
    public static final int HUB_TABS = 6;
    /** Default base speed of every machine: one operation per second. */
    public static final int DEFAULT_BASE_SPEED = 20;
    /** Slowest base speed the config allows: one operation per minute. */
    public static final int MAX_BASE_SPEED = 1200;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<Integer> MULTI_TRANSLOCATION_MAX_LINKS;
    private static final ModConfigSpec.ConfigValue<Integer> CRAFTER_BASE_SPEED;
    private static final ModConfigSpec.ConfigValue<Integer> MOVER_BASE_SPEED;
    private static final ModConfigSpec.ConfigValue<Integer> SMELTER_BASE_SPEED;
    private static final ModConfigSpec.ConfigValue<Integer> BREWER_BASE_SPEED;
    private static final ModConfigSpec.ConfigValue<Integer> MOVER_GRID_WIDTH;
    private static final ModConfigSpec.ConfigValue<Integer> MOVER_GRID_HEIGHT;
    private static final ModConfigSpec.ConfigValue<Integer> HUB_MACHINES_PER_TAB;
    private static final ModConfigSpec.ConfigValue<Integer> HUB_STARTING_TABS;
    private static final ModConfigSpec.ConfigValue<Integer> HUB_SPEED_PERCENT;
    private static final ModConfigSpec.ConfigValue<Boolean> HUB_EXTRA_SPEED_UPGRADES;
    private static final ModConfigSpec.ConfigValue<Boolean> HUB_USES_ENERGY;
    private static final ModConfigSpec.ConfigValue<Integer> HUB_ENERGY_PER_OPERATION;
    private static final ModConfigSpec.ConfigValue<Integer> HUB_ENERGY_CAPACITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.translation(BetterAutoCrafter.MODID + ".configuration.upgrades").push("upgrades");
        MULTI_TRANSLOCATION_MAX_LINKS = builder
                .comment("How many block faces one Multi Pull / Multi Push Upgrade (and their Dimensional versions) can link.",
                        "Lowering it keeps links made before, but machines only use the first ones, up to this many.")
                .translation(BetterAutoCrafter.MODID + ".configuration.multiTranslocationMaxLinks")
                .define("multiTranslocationMaxLinks", 16,
                        o -> o instanceof Integer i && i >= 1 && i <= 256);
        builder.pop();

        builder.translation(BetterAutoCrafter.MODID + ".configuration.betterAutoCrafter").push("betterAutoCrafter");
        CRAFTER_BASE_SPEED = defineBaseSpeed(builder, "Better Auto Crafter");
        builder.pop();

        builder.translation(BetterAutoCrafter.MODID + ".configuration.betterAutoMover").push("betterAutoMover");
        MOVER_BASE_SPEED = defineBaseSpeed(builder, "Better Auto Mover");
        MOVER_GRID_WIDTH = builder
                .comment("Columns of the Better Auto Mover's grid.",
                        "Shrinking the grid drops the items of the removed slots on top of existing movers.")
                .translation(BetterAutoCrafter.MODID + ".configuration.gridWidth")
                .worldRestart()
                .define("gridWidth", 3,
                        o -> o instanceof Integer i && i >= 1 && i <= MAX_GRID_WIDTH);
        MOVER_GRID_HEIGHT = builder
                .comment("Rows of the Better Auto Mover's grid.",
                        "Shrinking the grid drops the items of the removed slots on top of existing movers.")
                .translation(BetterAutoCrafter.MODID + ".configuration.gridHeight")
                .worldRestart()
                .define("gridHeight", 3,
                        o -> o instanceof Integer i && i >= 1 && i <= MAX_GRID_HEIGHT);
        builder.pop();

        builder.translation(BetterAutoCrafter.MODID + ".configuration.betterAutoSmelter").push("betterAutoSmelter");
        SMELTER_BASE_SPEED = defineBaseSpeed(builder, "Better Auto Smelter");
        builder.pop();

        builder.translation(BetterAutoCrafter.MODID + ".configuration.betterAutoBrewer").push("betterAutoBrewer");
        BREWER_BASE_SPEED = defineBaseSpeed(builder, "Better Auto Brewer");
        builder.pop();

        builder.translation(BetterAutoCrafter.MODID + ".configuration.betterAutoHub").push("betterAutoHub");
        HUB_MACHINES_PER_TAB = builder
                .comment("How many machines one tab of the Better Auto Hub holds. The tab runs as that many machines.",
                        "Lowering it keeps the machines already in tabs, but a tab only runs as this many.")
                .translation(BetterAutoCrafter.MODID + ".configuration.machinesPerTab")
                .define("machinesPerTab", 8,
                        o -> o instanceof Integer i && i >= 1 && i <= 64);
        HUB_STARTING_TABS = builder
                .comment("Tabs a new Better Auto Hub has. Each Hub Expansion used on it unlocks one more, up to " + HUB_TABS + ".",
                        "Lowering it keeps the machines in tabs that get locked, but they don't run until unlocked again.")
                .translation(BetterAutoCrafter.MODID + ".configuration.startingTabs")
                .define("startingTabs", 2,
                        o -> o instanceof Integer i && i >= 1 && i <= HUB_TABS);
        HUB_SPEED_PERCENT = builder
                .comment("Speed of the machines in a hub, in percent of the same machines' speed on their own.")
                .translation(BetterAutoCrafter.MODID + ".configuration.speedPercent")
                .define("speedPercent", 50,
                        o -> o instanceof Integer i && i >= 1 && i <= 100);
        HUB_EXTRA_SPEED_UPGRADES = builder
                .comment("Whether machines in a hub take more Speed Upgrades, enough to reach the fastest speed machines on",
                        "their own reach. When false, their fastest speed is only speedPercent of it.")
                .translation(BetterAutoCrafter.MODID + ".configuration.extraSpeedUpgrades")
                .define("extraSpeedUpgrades", false);
        HUB_USES_ENERGY = builder
                .comment("Whether the hub needs energy (FE) to run its machines. When false, it has no energy storage at all.")
                .translation(BetterAutoCrafter.MODID + ".configuration.usesEnergy")
                .worldRestart()
                .define("usesEnergy", false);
        HUB_ENERGY_PER_OPERATION = builder
                .comment("FE each operation of a machine in a hub uses, when usesEnergy is true.")
                .translation(BetterAutoCrafter.MODID + ".configuration.energyPerOperation")
                .define("energyPerOperation", 100,
                        o -> o instanceof Integer i && i >= 0 && i <= 1_000_000);
        HUB_ENERGY_CAPACITY = builder
                .comment("FE a hub stores, when usesEnergy is true.")
                .translation(BetterAutoCrafter.MODID + ".configuration.energyCapacity")
                .define("energyCapacity", 1_000_000,
                        o -> o instanceof Integer i && i >= 1 && i <= 1_000_000_000);
        builder.pop();
        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    /** Every machine's section has one: the ticks each of its operations takes. */
    private static ModConfigSpec.ConfigValue<Integer> defineBaseSpeed(ModConfigSpec.Builder builder, String machine) {
        return builder
                .comment("Ticks each operation of the " + machine + " takes (20 ticks = 1 second). Lower is faster.",
                        "It can't run again until they are over, whatever its redstone signal does.")
                .translation(BetterAutoCrafter.MODID + ".configuration.baseSpeed")
                .define("baseSpeed", DEFAULT_BASE_SPEED,
                        o -> o instanceof Integer i && i >= 1 && i <= MAX_BASE_SPEED);
    }

    /** Links per Multi Translocation Upgrade. The default until the config is loaded. */
    public static int multiTranslocationMaxLinks() {
        return get(MULTI_TRANSLOCATION_MAX_LINKS);
    }

    /** Ticks each Better Auto Crafter operation takes. */
    public static int crafterBaseSpeed() {
        return get(CRAFTER_BASE_SPEED);
    }

    /** Ticks each Better Auto Mover operation takes. */
    public static int moverBaseSpeed() {
        return get(MOVER_BASE_SPEED);
    }

    /** Ticks each Better Auto Smelter operation takes. */
    public static int smelterBaseSpeed() {
        return get(SMELTER_BASE_SPEED);
    }

    /** Ticks each Better Auto Brewer operation takes. */
    public static int brewerBaseSpeed() {
        return get(BREWER_BASE_SPEED);
    }

    public static int moverGridWidth() {
        return get(MOVER_GRID_WIDTH);
    }

    public static int moverGridHeight() {
        return get(MOVER_GRID_HEIGHT);
    }

    /** Machines one tab of the Better Auto Hub holds and runs as. */
    public static int hubMachinesPerTab() {
        return get(HUB_MACHINES_PER_TAB);
    }

    /** Unlocked tabs of a new Better Auto Hub. */
    public static int hubStartingTabs() {
        return get(HUB_STARTING_TABS);
    }

    /** Speed of a hub's machines, in percent of their normal speed. */
    public static int hubSpeedPercent() {
        return get(HUB_SPEED_PERCENT);
    }

    /** Whether a hub's machines take enough Speed Upgrades to reach the normal fastest speed. */
    public static boolean hubExtraSpeedUpgrades() {
        return getBoolean(HUB_EXTRA_SPEED_UPGRADES);
    }

    /** Whether the hub needs energy: it only has an energy storage then. */
    public static boolean hubUsesEnergy() {
        return getBoolean(HUB_USES_ENERGY);
    }

    public static int hubEnergyPerOperation() {
        return get(HUB_ENERGY_PER_OPERATION);
    }

    public static int hubEnergyCapacity() {
        return get(HUB_ENERGY_CAPACITY);
    }

    private static boolean getBoolean(ModConfigSpec.ConfigValue<Boolean> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static int get(ModConfigSpec.ConfigValue<Integer> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}

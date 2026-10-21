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

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<Integer> MULTI_TRANSLOCATION_MAX_LINKS;
    private static final ModConfigSpec.ConfigValue<Integer> MOVER_GRID_WIDTH;
    private static final ModConfigSpec.ConfigValue<Integer> MOVER_GRID_HEIGHT;

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

        builder.translation(BetterAutoCrafter.MODID + ".configuration.betterAutoMover").push("betterAutoMover");
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
        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    /** Links per Multi Translocation Upgrade. The default until the config is loaded. */
    public static int multiTranslocationMaxLinks() {
        return get(MULTI_TRANSLOCATION_MAX_LINKS);
    }

    public static int moverGridWidth() {
        return get(MOVER_GRID_WIDTH);
    }

    public static int moverGridHeight() {
        return get(MOVER_GRID_HEIGHT);
    }

    private static int get(ModConfigSpec.ConfigValue<Integer> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}

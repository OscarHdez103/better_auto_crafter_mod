package laseta.bac;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config: {@code config/better_auto_crafter-server.toml}, or a copy in a world's {@code serverconfig} folder to
 * override it for that world. NeoForge sends it to joining clients, so tooltips show the server's values.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue MULTI_TRANSLOCATION_MAX_LINKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.translation(BetterAutoCrafter.MODID + ".configuration.upgrades").push("upgrades");
        MULTI_TRANSLOCATION_MAX_LINKS = builder
                .comment("How many block faces one Multi Pull / Multi Push Upgrade (and their Dimensional versions) can link.",
                        "Lowering it keeps links made before, but crafters only use the first ones, up to this many.")
                .translation(BetterAutoCrafter.MODID + ".configuration.multiTranslocationMaxLinks")
                .defineInRange("multiTranslocationMaxLinks", 16, 1, 256);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    /** Links per Multi Translocation Upgrade. The default until the config is loaded (e.g. in the main menu). */
    public static int multiTranslocationMaxLinks() {
        return SPEC.isLoaded() ? MULTI_TRANSLOCATION_MAX_LINKS.getAsInt() : MULTI_TRANSLOCATION_MAX_LINKS.getDefault();
    }
}

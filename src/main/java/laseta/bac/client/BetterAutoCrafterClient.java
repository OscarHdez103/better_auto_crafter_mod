package laseta.bac.client;

import laseta.bac.BetterAutoCrafter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: adds NeoForge's config screen to the mod list (server values can be edited there in a world). */
@Mod(value = BetterAutoCrafter.MODID, dist = Dist.CLIENT)
public final class BetterAutoCrafterClient {
    public BetterAutoCrafterClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}

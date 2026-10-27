package laseta.bac.client.screen.widget;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import laseta.bac.sideconfig.RelativeSide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** The textures a block's model actually uses on each of its faces, so face buttons look like the block itself. */
public final class FaceSprites {
    private FaceSprites() {
    }

    /** The texture of every face of {@code state}, for a block facing {@code facing}. */
    public static Map<RelativeSide, TextureAtlasSprite> of(BlockState state, Direction facing) {
        Map<RelativeSide, TextureAtlasSprite> sprites = new EnumMap<>(RelativeSide.class);
        for (RelativeSide side : RelativeSide.values()) {
            sprites.put(side, find(state, side.toDirection(facing)));
        }
        return sprites;
    }

    /** Texture actually used by the block model on that face, so resource packs / model changes are reflected. */
    public static TextureAtlasSprite find(BlockState state, Direction direction) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, null);
        return quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.getFirst().getSprite();
    }
}

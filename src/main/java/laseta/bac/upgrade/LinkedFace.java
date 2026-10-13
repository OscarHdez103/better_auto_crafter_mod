package laseta.bac.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.StreamCodec;

/**
 * One block linked by a Multi Translocation Upgrade, and the face of it the crafter uses: the face that was clicked,
 * the same way a hopper or pipe on that face would see the block's inventory (a furnace's bottom is its output, ...).
 */
public record LinkedFace(GlobalPos pos, Direction side) {
    public static final Codec<LinkedFace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(LinkedFace::pos),
            Direction.CODEC.fieldOf("side").forGetter(LinkedFace::side)
    ).apply(instance, LinkedFace::new));

    public static final StreamCodec<ByteBuf, LinkedFace> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, LinkedFace::pos,
            Direction.STREAM_CODEC, LinkedFace::side,
            LinkedFace::new);
}

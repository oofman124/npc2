package npc2.npc2.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import npc2.npc2.FakeNpcEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-budget resource survey that continuously visits loaded chunks in outward rings.
 * It never owns movement and only populates the NPC's categorized resource memory.
 */
public final class ResourceSurveyor {
    private static final int MAX_CHUNK_RING = 12;
    private static final int RECENTER_CHUNK_DISTANCE = 4;
    private static final int LOCAL_VERTICAL_RADIUS = 12;
    private static final int SURFACE_LOOKBACK = 40;

    private ResourceSurveyor() {
    }

    public static void tick(FakeNpcEntity npc, int blockBudget) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Survey survey = npc.getMemories().resourceSurvey;
        ChunkPos currentChunk = ChunkPos.containing(npc.blockPosition());
        if (survey == null
                || !survey.dimension.equals(level.dimension())
                || survey.complete
                || survey.center.getChessboardDistance(currentChunk) > RECENTER_CHUNK_DISTANCE) {
            survey = new Survey(level, currentChunk, npc.blockPosition().getY());
            npc.getMemories().resourceSurvey = survey;
        }

        int checked = 0;
        while (checked++ < blockBudget) {
            BlockPos pos = survey.next(level);
            if (pos == null) break;
            BlockResourceGathering.Kind kind = BlockResourceGathering.identify(
                    npc, level.getBlockState(pos), pos);
            if (kind != null && BlockResourceGathering.isDiscoverable(npc, pos)) {
                npc.getMemories().rememberResource(npc, kind.resource(), pos);
            }
        }
    }

    public static final class Survey {
        private final ResourceKey<Level> dimension;
        private final ChunkPos center;
        private final int centerY;
        private final List<ChunkPos> chunks;
        private int chunkIndex = -1;
        private int column;
        private int phase;
        private int y;
        private int surfaceMinY;
        private int surfaceMaxY;
        private @Nullable ChunkPos chunk;
        private boolean complete;

        private Survey(ServerLevel level, ChunkPos center, int centerY) {
            this.dimension = level.dimension();
            this.center = center;
            this.centerY = centerY;
            this.chunks = chunksInOutwardRings(center);
        }

        private @Nullable BlockPos next(ServerLevel level) {
            while (true) {
                if (this.chunk == null) {
                    if (++this.chunkIndex >= this.chunks.size()) {
                        this.complete = true;
                        return null;
                    }
                    ChunkPos candidate = this.chunks.get(this.chunkIndex);
                    if (!level.isLoaded(candidate.getMiddleBlockPosition(this.centerY))) continue;
                    this.chunk = candidate;
                    this.column = 0;
                    this.phase = 0;
                    this.y = Math.max(level.getMinY(), this.centerY - LOCAL_VERTICAL_RADIUS);
                }

                if (this.column >= 256) {
                    this.chunk = null;
                    continue;
                }

                int localX = this.column & 15;
                int localZ = this.column >> 4;
                int x = this.chunk.getBlockX(localX);
                int z = this.chunk.getBlockZ(localZ);
                int bandMinY = Math.max(level.getMinY(), this.centerY - LOCAL_VERTICAL_RADIUS);
                int bandMaxY = Math.min(level.getMaxY() - 1, this.centerY + LOCAL_VERTICAL_RADIUS);
                if (this.phase == 0) {
                    if (this.y <= bandMaxY) return new BlockPos(x, this.y++, z);
                    this.surfaceMaxY = Math.min(level.getMaxY() - 1,
                            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
                    this.surfaceMinY = Math.max(level.getMinY(), this.surfaceMaxY - SURFACE_LOOKBACK);
                    this.y = this.surfaceMinY;
                    this.phase = 1;
                }
                while (this.phase == 1 && this.y <= this.surfaceMaxY) {
                    int candidateY = this.y++;
                    if (candidateY < bandMinY || candidateY > bandMaxY) {
                        return new BlockPos(x, candidateY, z);
                    }
                }
                this.column++;
                this.phase = 0;
                this.y = bandMinY;
            }
        }

        public int currentRing() {
            if (this.chunkIndex < 0 || this.chunkIndex >= this.chunks.size()) return 0;
            return this.center.getChessboardDistance(this.chunks.get(this.chunkIndex));
        }

        private static List<ChunkPos> chunksInOutwardRings(ChunkPos center) {
            List<ChunkPos> chunks = new ArrayList<>((MAX_CHUNK_RING * 2 + 1) * (MAX_CHUNK_RING * 2 + 1));
            chunks.add(center);
            for (int ring = 1; ring <= MAX_CHUNK_RING; ring++) {
                for (int offset = -ring; offset <= ring; offset++) {
                    chunks.add(new ChunkPos(center.x() + offset, center.z() - ring));
                    chunks.add(new ChunkPos(center.x() + offset, center.z() + ring));
                }
                for (int offset = -ring + 1; offset < ring; offset++) {
                    chunks.add(new ChunkPos(center.x() - ring, center.z() + offset));
                    chunks.add(new ChunkPos(center.x() + ring, center.z() + offset));
                }
            }
            return List.copyOf(chunks);
        }
    }
}

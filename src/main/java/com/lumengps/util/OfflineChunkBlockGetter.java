package com.lumengps.util;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A BlockGetter that reads block states from in-memory chunks or from
 * previously-explored chunks saved to disk (.mca region files).
 *
 * This allows the A* pathfinder to navigate through terrain that is not
 * currently loaded in server memory, as long as it was previously explored.
 *
 * All disk I/O happens on a Virtual Thread (never the server main thread).
 * NOT thread-safe; create one instance per pathfinding operation.
 */
public final class OfflineChunkBlockGetter implements BlockGetter {

    private static final long DISK_READ_TIMEOUT_MS = 2_000;

    private final ServerLevel level;
    private final Map<Long, LevelChunkSection[]> sectionCache = new HashMap<>();
    private final Codec<PalettedContainer<BlockState>> blockStatesCodec;

    public OfflineChunkBlockGetter(ServerLevel level) {
        this.level = level;
        PalettedContainerFactory factory = PalettedContainerFactory.create(level.registryAccess());
        this.blockStatesCodec = factory.blockStatesContainerCodec();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // Pathfinder does not need block entities - return null
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.pack(cx, cz);

        LevelChunkSection[] sections;
        if (sectionCache.containsKey(key)) {
            sections = sectionCache.get(key);
        } else {
            sections = resolveChunk(cx, cz);
            sectionCache.put(key, sections);
        }

        if (sections == null) return Blocks.AIR.defaultBlockState();

        int sectionIdx = level.getSectionIndex(pos.getY());
        if (sectionIdx < 0 || sectionIdx >= sections.length) return Blocks.AIR.defaultBlockState();

        LevelChunkSection section = sections[sectionIdx];
        if (section == null || section.hasOnlyAir()) return Blocks.AIR.defaultBlockState();

        return section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinY() {
        return level.getMinY();
    }

    private LevelChunkSection[] resolveChunk(int cx, int cz) {
        // Fast path: chunk already in memory
        LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
        if (loaded != null) {
            return loaded.getSections();
        }

        // Slow path: read from disk via ChunkMap (extends SimpleRegionStorage)
        try {
            ChunkPos cp = new ChunkPos(cx, cz);
            Optional<CompoundTag> tagOpt = level.getChunkSource().chunkMap
                    .read(cp)
                    .get(DISK_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (tagOpt.isEmpty()) return null;

            return parseSections(tagOpt.get());
        } catch (Exception e) {
            com.lumengps.LumenGPS.LOGGER.warn("[LumenGPS] Failed to read chunk [{},{}] from disk: {}", cx, cz, e.getMessage());
            return null;
        }
    }

    private LevelChunkSection[] parseSections(CompoundTag chunkTag) {
        int minSection = level.getMinSectionY();
        int maxSection = level.getMaxSectionY() + 1;
        int sectionCount = maxSection - minSection;
        LevelChunkSection[] sections = new LevelChunkSection[sectionCount];

        // MC 26.x: getListOrEmpty() returns empty ListTag if absent
        ListTag sectionList = chunkTag.getListOrEmpty("sections");
        if (sectionList.isEmpty()) return sections;

        PalettedContainerFactory factory = PalettedContainerFactory.create(level.registryAccess());

        for (int i = 0; i < sectionList.size(); i++) {
            // MC 26.x: getCompound() returns Optional, use getCompoundOrEmpty()
            CompoundTag sectionTag = sectionList.getCompound(i).orElse(null);
            if (sectionTag == null) continue;

            // MC 26.x: getByte() returns Optional<Byte>, use getByteOr()
            int y = sectionTag.getByteOr("Y", (byte) 0);
            int idx = y - minSection;
            if (idx < 0 || idx >= sectionCount) continue;

            // MC 26.x: contains() only takes String key
            if (!sectionTag.contains("block_states")) continue;

            try {
                // MC 26.x: getCompound() returns Optional<CompoundTag>
                CompoundTag blockStatesTag = sectionTag.getCompoundOrEmpty("block_states");
                PalettedContainer<BlockState> states = blockStatesCodec
                        .parse(NbtOps.INSTANCE, blockStatesTag)
                        .getOrThrow();

                LevelChunkSection section = new LevelChunkSection(states, factory.createForBiomes());
                section.recalcBlockCounts();
                sections[idx] = section;
            } catch (Exception ignored) {
                // Treat failed sections as air
            }
        }

        return sections;
    }
}
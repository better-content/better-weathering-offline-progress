package com.bettercontent.betterweatheringofflineprogress;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ImmersiveWeatheringSampler.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SamplerGameTests {
    private static final TicketType<ChunkPos> RELOAD_WITNESS = TicketType.create(
            "weathering_reload_witness", Comparator.comparingLong(ChunkPos::toLong));

    private SamplerGameTests() {
    }

    public static void register(final RegisterGameTestsEvent event) {
        ImmersiveWeatheringSampler.LOGGER.info("Registering Better Weathering Offline Progress game tests");
        event.register(SamplerGameTests.class);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void exposureSnapshotRoundTrips(final GameTestHelper helper) {
        final ExposureClock expected = new ExposureClock(120, 80, 40, 20);
        final CompoundTag tag = new CompoundTag();
        expected.save(tag, "Exposure");
        helper.assertTrue(expected.equals(ExposureClock.load(tag, "Exposure")), "exposure snapshot must be lossless");
        helper.assertTrue(expected.total() == 260, "partitioned exposure must preserve elapsed time");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void saveCallbackPreservesSnapshotWithoutRedirtying(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var chunk = level.getChunkAt(helper.absolutePos(BlockPos.ZERO));
        final boolean originalDirty = chunk.isUnsaved();
        final CompoundTag originalSnapshot = new CompoundTag();
        ChunkExposureData.save(level, chunk, originalSnapshot);
        try {
            // Normal world changes must still schedule persistence.
            ChunkExposureData.write(level, chunk, new ExposureClock(1, 2, 3, 4));
            helper.assertTrue(chunk.isUnsaved(), "ordinary snapshot updates must remain dirty");
            final ExposureClock expected = ExposureSavedData.get(level).clock();
            // Mirror Minecraft's clear-before-save and use the real callback.
            for (int pass = 0; pass < 2; pass++) {
                chunk.setUnsaved(false);
                final CompoundTag saved = new CompoundTag();
                SamplerEvents.onChunkSave(new ChunkDataEvent.Save(chunk, level, saved));
                helper.assertTrue(!chunk.isUnsaved(), "a save callback must not force another native save pass");
                ChunkExposureData.load(level, chunk, saved);
                helper.assertTrue(ChunkExposureData.initialized(level, chunk), "schema must survive native save NBT");
                helper.assertTrue(ChunkExposureData.read(level, chunk).equals(expected), "current exposure snapshot must survive save/load unchanged");
            }
            // Never erase a dirty flag belonging to a real concurrent change.
            chunk.setUnsaved(true);
            SamplerEvents.onChunkSave(new ChunkDataEvent.Save(chunk, level, new CompoundTag()));
            helper.assertTrue(chunk.isUnsaved(), "pre-existing dirty flags must remain untouched");
        } finally {
            ChunkExposureData.load(level, chunk, originalSnapshot);
            chunk.setUnsaved(originalDirty);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void configurableNeighborhoodDoesNotLoadMissingChunks(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var origin = helper.absolutePos(BlockPos.ZERO);
        final var fullChunkLoaded = (java.util.function.Predicate<BlockPos>) pos ->
                level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16)) != null;
        helper.assertTrue(EndpointSampler.configurableNeighborhoodLoaded(origin, 0, fullChunkLoaded),
                "the loaded GameTest neighborhood must retain native growth opportunities");
        final var missing = origin.offset(1_000_000, 0, 1_000_000);
        helper.assertTrue(!fullChunkLoaded.test(missing), "regression fixture must begin without a FULL chunk");
        helper.assertTrue(!EndpointSampler.configurableNeighborhoodLoaded(missing, 8, fullChunkLoaded),
                "an unloaded native area must skip rather than request chunk promotion");
        helper.assertTrue(!fullChunkLoaded.test(missing), "neighborhood validation must not load or generate the missing chunk");
        helper.succeed();
    }

    /** A fresh disposable native fixture, not a claim of disk restart or a guaranteed random block change.
     * No gamerule, weather, world time, block or persistent ticket is changed by this test. The
     * remote chunk must emit Forge's actual unload event without a forced-unload helper call.
     * After that native unload, a short-lived fixture ticket holds only the deliberate reload
     * through its first level tick; the ticket is removed even if the assertion fails.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 2000)
    public static void nativeUnloadedChunkReloadRefreshesExposureSnapshot(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final var source = level.getChunkSource();
        final BlockPos fixture = helper.absolutePos(BlockPos.ZERO);
        // Far outside the GameTest and spawn tickets, but within ordinary world bounds.
        final int x = Math.floorDiv(fixture.getX(), 16) + 512;
        final int z = Math.floorDiv(fixture.getZ(), 16) + 512;
        helper.assertTrue(source.getChunkNow(x, z) == null,
                "Fresh remote fixture must not start as a loaded FULL chunk");
        final LevelChunk first = level.getChunk(x, z); // Deliberate initial fixture load, not an assertion.
        helper.assertTrue(ChunkExposureData.initialized(level, first),
                "Native first-load event must initialize a chunk snapshot");
        final ExposureClock initial = ChunkExposureData.read(level, first);
        final UnloadWitness witness = new UnloadWitness(first);
        MinecraftForge.EVENT_BUS.register(witness);
        awaitNativeUnload(helper, level, x, z, initial, level.getGameTime(), witness);
    }

    public static final class UnloadWitness {
        private final LevelChunk original;
        private boolean unloaded;

        private UnloadWitness(final LevelChunk original) {
            this.original = original;
        }

        @SubscribeEvent
        public void onUnload(final ChunkEvent.Unload event) {
            if (event.getChunk() == original) unloaded = true;
        }
    }

    private static void awaitNativeUnload(final GameTestHelper helper, final ServerLevel level,
                                          final int x, final int z, final ExposureClock initial, final long began,
                                          final UnloadWitness witness) {
        final long now = level.getGameTime();
        if (!witness.unloaded && now - began < 1700) {
            helper.runAfterDelay(5, () -> awaitNativeUnload(helper, level, x, z, initial, began, witness));
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(witness);
        helper.assertTrue(witness.unloaded, "Remote chunk did not emit a native unload event");
        // Losing FULL status alone can leave the original chunk in the pending-unload map.
        awaitElapsedUnloaded(helper, level, x, z, initial, now);
    }

    private static void awaitElapsedUnloaded(final GameTestHelper helper, final ServerLevel level,
                                              final int x, final int z, final ExposureClock initial, final long unloaded) {
        final long elapsed = level.getGameTime() - unloaded;
        final int minimum = SamplerConfig.MINIMUM_UNLOADED_TICKS.get();
        helper.assertTrue(minimum <= 200,
                "Configured minimum exceeds this bounded native fixture; do not change global config");
        helper.assertTrue(elapsed < 250,
                "No bounded unloaded exposure window was available");
        helper.assertTrue(level.getChunkSource().getChunkNow(x, z) == null,
                "Remote FULL chunk reloaded before deliberate fixture action");
        if (elapsed < Math.max(100, minimum)) {
            helper.runAfterDelay(5, () -> awaitElapsedUnloaded(helper, level, x, z, initial, unloaded));
            return;
        }
        final ExposureClock beforeReload = ExposureSavedData.get(level).clock();
        final LevelChunk returning = level.getChunk(x, z); // Deliberate reload ONLY after observed absence.
        helper.assertTrue(ChunkExposureData.initialized(level, returning),
                "Reloaded chunk lost the native snapshot; save/load path is not proven");
        final ExposureClock restored = ChunkExposureData.read(level, returning);
        helper.assertTrue(restored.total() >= initial.total() && restored.total() <= beforeReload.total(),
                "Reload must restore a bounded unload snapshot before the queued native tick");
        final ChunkPos position = new ChunkPos(x, z);
        level.getChunkSource().addRegionTicket(RELOAD_WITNESS, position, 0, position);
        helper.runAfterDelay(2, () -> {
            try {
                final LevelChunk observed = level.getChunkSource().getChunkNow(x, z);
                helper.assertTrue(observed != null && ChunkExposureData.initialized(level, observed),
                        "Reloaded FULL chunk must remain available through the first native level tick");
                final ExposureClock updated = ChunkExposureData.read(level, observed);
                helper.assertTrue(updated.total() >= beforeReload.total() && updated.total() > restored.total(),
                        "Native level tick must advance the reloaded snapshot across the unloaded interval");
                // No block-change claim: endpoint actions are chance/config/provider dependent.
                helper.succeed();
            } finally {
                level.getChunkSource().removeRegionTicket(RELOAD_WITNESS, position, 0, position);
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void endpointProbabilityIsBounded(final GameTestHelper helper) {
        final double shortAbsence = ProbabilityMath.atLeastOne(20, 0.001D);
        final double longAbsence = ProbabilityMath.atLeastOne(20_000, 0.001D);
        helper.assertTrue(shortAbsence > 0.0D && shortAbsence < 1.0D, "short endpoint chance must be bounded");
        helper.assertTrue(longAbsence > shortAbsence && longAbsence <= 1.0D, "elapsed exposure must only increase chance");
        helper.succeed();
    }
}

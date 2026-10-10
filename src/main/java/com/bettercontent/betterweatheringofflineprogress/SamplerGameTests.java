package com.bettercontent.betterweatheringofflineprogress;

import net.minecraft.core.BlockPos;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ImmersiveWeatheringSampler.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SamplerGameTests {
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

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void endpointProbabilityIsBounded(final GameTestHelper helper) {
        final double shortAbsence = ProbabilityMath.atLeastOne(20, 0.001D);
        final double longAbsence = ProbabilityMath.atLeastOne(20_000, 0.001D);
        helper.assertTrue(shortAbsence > 0.0D && shortAbsence < 1.0D, "short endpoint chance must be bounded");
        helper.assertTrue(longAbsence > shortAbsence && longAbsence <= 1.0D, "elapsed exposure must only increase chance");
        helper.succeed();
    }
}

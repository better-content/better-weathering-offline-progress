package com.bettercontent.betterweatheringofflineprogress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

final class EndpointSamplingPolicyTest {
    @Test
    void fixedSeedHasStableAcceptanceAndRejectionCounts() {
        final RandomSource random = RandomSource.create(0x5EED_0707L);
        int accepted = 0;
        final int attempts = 20_000;
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (ProbabilityMath.occurs(random, 240L, 0.0002D)) accepted++;
        }
        assertEquals(1014, accepted, "fixed seed guards both accepted and rejected endpoint samples");
    }

    @Test
    void unloadedOrZeroExposureNeverCreatesAnEndpointOpportunity() {
        final RandomSource random = RandomSource.create(12345L);
        assertFalse(ProbabilityMath.occurs(random, 0L, 1.0D));
        assertFalse(ProbabilityMath.occurs(random, 400L, 0.0D));
        assertTrue(ProbabilityMath.occurs(RandomSource.create(12345L), 400L, 1.0D));
    }

    @Test
    void splittingAndReplayingAnExposureDoesNotIncreaseTheSingleEndpointBound() {
        final double oneEndpoint = ProbabilityMath.atLeastOne(800L, 0.0005D);
        final double firstHalf = ProbabilityMath.atLeastOne(400L, 0.0005D);
        final double secondHalf = ProbabilityMath.atLeastOne(400L, 0.0005D);
        assertEquals(oneEndpoint, 1.0D - (1.0D - firstHalf) * (1.0D - secondHalf), 1.0E-15D);
        assertTrue(oneEndpoint <= 1.0D);
    }

    @Test
    void configurableSamplingRequiresFullAreaAndGrowthTargetNeighborhood() {
        final var originChunkOnly = (java.util.function.Predicate<BlockPos>) pos ->
                Math.floorDiv(pos.getX(), 16) == 0 && Math.floorDiv(pos.getZ(), 16) == 0;
        assertTrue(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(8, 64, 8), 0, originChunkOnly));
        assertFalse(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(15, 64, 8), 0, originChunkOnly),
                "even an empty area condition may grow a double target across a chunk edge");
        assertFalse(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(8, 64, 8), 9, originChunkOnly),
                "the declared area radius must take precedence over the target margin");
        assertFalse(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(8, 64, 8), 0, pos -> false));
        assertTrue(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(-8, 64, -8), 0,
                pos -> Math.floorDiv(pos.getX(), 16) == -1 && Math.floorDiv(pos.getZ(), 16) == -1));
    }

    @Test
    void configurableSamplingChecksInteriorChunksNotOnlyCorners() {
        final var checked = new java.util.HashSet<BlockPos>();
        assertTrue(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(8, 64, 8), 32,
                pos -> { checked.add(pos); return true; }));
        assertEquals(25, checked.size());
        assertFalse(EndpointSampler.configurableNeighborhoodLoaded(new BlockPos(8, 64, 8), 32,
                pos -> !(pos.getX() == 0 && pos.getZ() == 16)),
                "a missing interior FULL chunk must reject the native area scan");
    }

    @Test
    void icicleSamplingSkipsBoundariesWhenANeighborChunkIsNotLoaded() {
        final var originChunkOnly = (java.util.function.Predicate<BlockPos>) pos ->
                Math.floorDiv(pos.getX(), 16) == 0 && Math.floorDiv(pos.getZ(), 16) == 0;

        assertFalse(EndpointSampler.snowIcicleNeighborhoodLoaded(new BlockPos(0, 64, 8), 1, originChunkOnly));
        assertTrue(EndpointSampler.snowIcicleNeighborhoodLoaded(new BlockPos(8, 64, 8), 1, originChunkOnly));
        assertFalse(EndpointSampler.snowIcicleNeighborhoodLoaded(new BlockPos(8, 64, 8), 1, pos -> false));
    }
}

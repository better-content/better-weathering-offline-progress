package com.bettercontent.betterweatheringofflineprogress;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SamplerEventsTest {
    @Test void chunkIoThreadsShareOneConcurrentDimensionQueue() throws Exception {
        Map<String, Set<Long>> queues = new ConcurrentHashMap<>();
        int workers = 16;
        int additions = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                final int offset = worker * additions;
                results.add(executor.submit(() -> {
                    start.await();
                    Set<Long> queue = SamplerEvents.queue(queues, "overworld");
                    for (int index = 0; index < additions; index++) queue.add((long) offset + index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(Set.of("overworld"), queues.keySet());
        assertEquals(workers * additions, queues.get("overworld").size());
    }

    @Test void chunkIoThreadsShareOneConcurrentExposureIndex() throws Exception {
        Map<String, Map<Long, Integer>> dimensions = new ConcurrentHashMap<>();
        int workers = 16;
        int additions = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                final int offset = worker * additions;
                results.add(executor.submit(() -> {
                    start.await();
                    Map<Long, Integer> entries = ChunkExposureData.entriesFor(dimensions, "overworld");
                    for (int index = 0; index < additions; index++) entries.put((long) offset + index, index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(Set.of("overworld"), dimensions.keySet());
        assertEquals(workers * additions, dimensions.get("overworld").size());
    }
}

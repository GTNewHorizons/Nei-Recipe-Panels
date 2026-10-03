package com.gtnewhorizons.neirecipepanel.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class BoundedPlayerTaskQueueTest {

    @Test
    void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedPlayerTaskQueue<>(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BoundedPlayerTaskQueue<>(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedPlayerTaskQueue<>(1, 2));
    }

    @Test
    void enforcesPlayerLimitAndReleasesCapacityAfterExecution() {
        BoundedPlayerTaskQueue<String, Integer> queue = new BoundedPlayerTaskQueue<>(4, 2);
        assertTrue(queue.offer("player", 1));
        assertTrue(queue.offer("player", 2));
        assertFalse(queue.offer("player", 3));
        assertEquals(2, queue.size());

        assertEquals(Integer.valueOf(1), queue.poll());
        assertTrue(queue.offer("player", 3));
        assertEquals(Integer.valueOf(2), queue.poll());
        assertEquals(Integer.valueOf(3), queue.poll());
        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test
    void enforcesGlobalLimitAcrossDifferentPlayers() {
        BoundedPlayerTaskQueue<String, Integer> queue = new BoundedPlayerTaskQueue<>(3, 2);
        assertTrue(queue.offer("a", 1));
        assertTrue(queue.offer("b", 2));
        assertTrue(queue.offer("c", 3));
        assertFalse(queue.offer("d", 4));
        assertFalse(queue.offer("a", 4));
        assertEquals(3, queue.size());

        assertEquals(Integer.valueOf(1), queue.poll());
        assertTrue(queue.offer("d", 4));
        assertEquals(3, queue.size());
    }

    @Test
    void runsPlayersRoundRobinAndPreservesTheirTaskOrder() {
        BoundedPlayerTaskQueue<String, String> queue = new BoundedPlayerTaskQueue<>(6, 3);
        assertTrue(queue.offer("a", "a1"));
        assertTrue(queue.offer("a", "a2"));
        assertTrue(queue.offer("a", "a3"));
        assertTrue(queue.offer("b", "b1"));
        assertTrue(queue.offer("b", "b2"));
        assertTrue(queue.offer("c", "c1"));

        assertEquals("a1", queue.poll());
        assertEquals("b1", queue.poll());
        assertEquals("c1", queue.poll());
        assertEquals("a2", queue.poll());
        assertEquals("b2", queue.poll());
        assertEquals("a3", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void clearingRemovesTasksAndResetsPlayerAndGlobalCapacity() {
        BoundedPlayerTaskQueue<String, Integer> queue = new BoundedPlayerTaskQueue<>(2, 1);
        queue.offer("a", 1);
        queue.offer("b", 2);
        queue.clear();

        assertEquals(0, queue.size());
        assertNull(queue.poll());
        assertTrue(queue.offer("a", 3));
        assertTrue(queue.offer("b", 4));
        assertEquals(Integer.valueOf(3), queue.poll());
        assertEquals(Integer.valueOf(4), queue.poll());
    }

    @Test
    void concurrentProducersCannotExceedThePlayerLimit() throws Exception {
        BoundedPlayerTaskQueue<Integer, Integer> queue = new BoundedPlayerTaskQueue<>(64, 8);
        assertEquals(8, submitConcurrently(queue, false));
        assertEquals(8, queue.size());
    }

    @Test
    void concurrentProducersCannotExceedTheGlobalLimit() throws Exception {
        BoundedPlayerTaskQueue<Integer, Integer> queue = new BoundedPlayerTaskQueue<>(64, 8);
        assertEquals(64, submitConcurrently(queue, true));
        assertEquals(64, queue.size());
    }

    private static int submitConcurrently(BoundedPlayerTaskQueue<Integer, Integer> queue, boolean separatePlayers)
        throws Exception {
        ExecutorService producers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int producer = 0; producer < 16; producer++) {
                int player = separatePlayers ? producer : 0;
                results.add(producers.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Producers were not started");
                    }
                    int accepted = 0;
                    for (int task = 0; task < 100; task++) {
                        if (queue.offer(player, task)) {
                            accepted++;
                        }
                    }
                    return accepted;
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Integer> result : results) {
                accepted += result.get(5, TimeUnit.SECONDS);
            }
            return accepted;
        } finally {
            start.countDown();
            producers.shutdownNow();
            assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

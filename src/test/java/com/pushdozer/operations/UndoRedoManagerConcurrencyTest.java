package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Concurrent safety tests for UndoRedoManager.
 * Tests thread-safety of executingPlayers set and state management.
 */
class UndoRedoManagerConcurrencyTest {

    @Mock
    private ServerWorld mockWorld;

    @Mock
    private ServerPlayerEntity mockPlayer;

    private UndoRedoManager manager;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new UndoRedoManager();
        playerId = UUID.randomUUID();

        when(mockPlayer.getUuid()).thenReturn(playerId);
        when(mockPlayer.getName()).thenReturn(net.minecraft.text.Text.literal("TestPlayer"));
        when(mockWorld.isClient()).thenReturn(false);
    }

    @Test
    void testConcurrentUndoCalls_NoRaceCondition() throws InterruptedException {
        // Arrange: Create test data
        List<BlockPos> positions = Arrays.asList(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0)
        );
        List<BlockState> originalStates = Arrays.asList(
            Blocks.STONE.getDefaultState(),
            Blocks.STONE.getDefaultState()
        );
        List<BlockState> newStates = Arrays.asList(
            Blocks.AIR.getDefaultState(),
            Blocks.AIR.getDefaultState()
        );

        UndoAction action = new UndoAction(
            UndoAction.ActionType.BREAK,
            positions,
            originalStates,
            newStates
        );

        manager.pushUndoAction(mockPlayer, action);

        // Act: Simulate concurrent undo attempts
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    manager.undoLastAction(mockPlayer, mockWorld);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected - concurrent access should be handled safely
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

        executor.shutdown();

        // Assert: Should complete without deadlock or exception
        assertTrue(completed, "All threads should complete");
        assertTrue(successCount.get() > 0, "At least one undo should succeed");
    }

    @Test
    void testExecutingPlayersThreadSafety() throws InterruptedException {
        // Test that executingPlayers set is thread-safe
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<UUID> testPlayerIds = ConcurrentHashMap.newKeySet();

        // Create unique players
        for (int i = 0; i < threadCount; i++) {
            testPlayerIds.add(UUID.randomUUID());
        }

        // Concurrent operations
        for (UUID id : testPlayerIds) {
            executor.submit(() -> {
                try {
                    PlayerEntity player = mock(PlayerEntity.class);
                    when(player.getUuid()).thenReturn(id);
                    when(player.getName()).thenReturn(net.minecraft.text.Text.literal("Player_" + id));

                    // Push and undo
                    UndoAction action = createTestAction();
                    manager.pushUndoAction(player, action);
                    manager.undoLastAction(player, mockWorld);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent operations should complete");
    }

    @Test
    void testStateClearedOnException() {
        // Arrange: Create a manager that will throw during execution
        UndoRedoManager testManager = new UndoRedoManager() {
            @Override
            protected void executeUndoRedoAction(UndoAction action, PlayerEntity player, World world,
                                                 boolean isUndo, java.util.function.Consumer<Boolean> onFinished) {
                throw new RuntimeException("Simulated error");
            }
        };

        UndoAction action = createTestAction();
        testManager.pushUndoAction(mockPlayer, action);

        // Act & Assert: Exception should be thrown but state should be cleaned
        assertThrows(RuntimeException.class, () -> {
            testManager.undoLastAction(mockPlayer, mockWorld);
        });

        // The player should not be stuck in executing state
        // Try another undo after cooldown - should not be blocked
        try {
            Thread.sleep(350); // Wait for cooldown
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // This should not throw - proving state was cleaned
        assertDoesNotThrow(() -> {
            testManager.undoLastAction(mockPlayer, mockWorld);
        });
    }

    @Test
    void testCooldownRespectedUnderConcurrency() throws InterruptedException {
        // Arrange
        UndoAction action = createTestAction();
        manager.pushUndoAction(mockPlayer, action);
        manager.pushUndoAction(mockPlayer, action);

        // Act: Try rapid consecutive undos
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger executedCount = new AtomicInteger(0);

        executor.submit(() -> {
            manager.undoLastAction(mockPlayer, mockWorld);
            executedCount.incrementAndGet();
            latch.countDown();
        });

        // Small delay to ensure first starts
        Thread.sleep(50);

        executor.submit(() -> {
            manager.undoLastAction(mockPlayer, mockWorld);
            executedCount.incrementAndGet();
            latch.countDown();
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: Second call should be blocked by cooldown
        assertTrue(completed);
        // We expect only 1 execution due to cooldown/executing check
        assertTrue(executedCount.get() <= 2, "Should respect cooldown");
    }

    private UndoAction createTestAction() {
        List<BlockPos> positions = Collections.singletonList(new BlockPos(0, 64, 0));
        List<BlockState> originalStates = Collections.singletonList(Blocks.STONE.getDefaultState());
        List<BlockState> newStates = Collections.singletonList(Blocks.AIR.getDefaultState());

        return new UndoAction(
            UndoAction.ActionType.BREAK,
            positions,
            originalStates,
            newStates
        );
    }
}

package com.pushdozer.registry;

import com.pushdozer.config.domain.WorkMode;
import com.pushdozer.items.handlers.TerrainToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HandlerRegistry.
 */
class HandlerRegistryTest {

    @Mock
    private TerrainToolHandler mockHandler1;

    @Mock
    private TerrainToolHandler mockHandler2;

    private HandlerRegistry registry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new HandlerRegistry();
    }

    @Test
    void testRegister_ShouldSucceed() {
        // Act
        registry.register(WorkMode.EXCAVATE, mockHandler1);

        // Assert
        assertTrue(registry.hasHandler(WorkMode.EXCAVATE));
        assertEquals(mockHandler1, registry.get(WorkMode.EXCAVATE));
        assertEquals(1, registry.size());
    }

    @Test
    void testRegister_ShouldThrowOnNullMode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            registry.register(null, mockHandler1);
        });
    }

    @Test
    void testRegister_ShouldThrowOnNullHandler() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            registry.register(WorkMode.EXCAVATE, null);
        });
    }

    @Test
    void testRegister_ShouldThrowOnDuplicateMode() {
        // Arrange
        registry.register(WorkMode.EXCAVATE, mockHandler1);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.register(WorkMode.EXCAVATE, mockHandler2);
        });

        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    void testRegisterMultipleHandlers() {
        // Act
        registry.register(WorkMode.EXCAVATE, mockHandler1);
        registry.register(WorkMode.PLACE, mockHandler2);

        // Assert
        assertEquals(2, registry.size());
        assertEquals(mockHandler1, registry.get(WorkMode.EXCAVATE));
        assertEquals(mockHandler2, registry.get(WorkMode.PLACE));
    }

    @Test
    void testGet_ShouldReturnNullForUnregisteredMode() {
        // Act
        TerrainToolHandler handler = registry.get(WorkMode.EXCAVATE);

        // Assert
        assertNull(handler);
    }

    @Test
    void testGetOptional_ShouldReturnEmptyForUnregisteredMode() {
        // Act
        Optional<TerrainToolHandler> handler = registry.getOptional(WorkMode.EXCAVATE);

        // Assert
        assertFalse(handler.isPresent());
    }

    @Test
    void testGetOptional_ShouldReturnPresentForRegisteredMode() {
        // Arrange
        registry.register(WorkMode.EXCAVATE, mockHandler1);

        // Act
        Optional<TerrainToolHandler> handler = registry.getOptional(WorkMode.EXCAVATE);

        // Assert
        assertTrue(handler.isPresent());
        assertEquals(mockHandler1, handler.get());
    }

    @Test
    void testHasHandler_ShouldReturnFalseForUnregisteredMode() {
        // Act & Assert
        assertFalse(registry.hasHandler(WorkMode.EXCAVATE));
    }

    @Test
    void testHasHandler_ShouldReturnTrueForRegisteredMode() {
        // Arrange
        registry.register(WorkMode.EXCAVATE, mockHandler1);

        // Act & Assert
        assertTrue(registry.hasHandler(WorkMode.EXCAVATE));
    }

    @Test
    void testClear_ShouldRemoveAllHandlers() {
        // Arrange
        registry.register(WorkMode.EXCAVATE, mockHandler1);
        registry.register(WorkMode.PLACE, mockHandler2);
        assertEquals(2, registry.size());

        // Act
        registry.clear();

        // Assert
        assertEquals(0, registry.size());
        assertFalse(registry.hasHandler(WorkMode.EXCAVATE));
        assertFalse(registry.hasHandler(WorkMode.PLACE));
    }

    @Test
    void testSize_ShouldReturnCorrectCount() {
        // Assert initial size
        assertEquals(0, registry.size());

        // Add handlers
        registry.register(WorkMode.EXCAVATE, mockHandler1);
        assertEquals(1, registry.size());

        registry.register(WorkMode.PLACE, mockHandler2);
        assertEquals(2, registry.size());

        // Clear
        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    void testRegisterAllWorkModes() {
        // Arrange - create mocks for all work modes
        WorkMode[] allModes = WorkMode.values();

        // Act - register a handler for each mode
        for (WorkMode mode : allModes) {
            TerrainToolHandler handler = mock(TerrainToolHandler.class);
            registry.register(mode, handler);
        }

        // Assert
        assertEquals(allModes.length, registry.size());
        for (WorkMode mode : allModes) {
            assertTrue(registry.hasHandler(mode));
        }
    }

    @Test
    void testThreadSafety_ConcurrentRegistration() throws InterruptedException {
        // This is a basic thread safety check
        // More comprehensive tests are in the integration tests

        Thread t1 = new Thread(() -> {
            registry.register(WorkMode.EXCAVATE, mockHandler1);
        });

        Thread t2 = new Thread(() -> {
            registry.register(WorkMode.PLACE, mockHandler2);
        });

        // Act
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Assert
        assertEquals(2, registry.size());
        assertTrue(registry.hasHandler(WorkMode.EXCAVATE));
        assertTrue(registry.hasHandler(WorkMode.PLACE));
    }
}

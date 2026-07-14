package com.pushdozer.registry;

import com.pushdozer.config.PushdozerConfig.WorkMode;
import com.pushdozer.items.handlers.TerrainToolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for managing terrain tool handlers.
 * Provides a centralized, type-safe way to register and retrieve handlers
 * instead of using static fields.
 */
public class HandlerRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private final Map<WorkMode, TerrainToolHandler> handlers = new EnumMap<>(WorkMode.class);

    /**
     * Register a handler for a specific work mode.
     *
     * @param mode    the work mode
     * @param handler the handler implementation
     * @throws IllegalArgumentException if a handler is already registered for this mode
     */
    public void register(WorkMode mode, TerrainToolHandler handler) {
        if (mode == null || handler == null) {
            throw new IllegalArgumentException("WorkMode and handler must not be null");
        }

        if (handlers.containsKey(mode)) {
            throw new IllegalArgumentException("Handler already registered for mode: " + mode);
        }

        handlers.put(mode, handler);
        LOGGER.debug("Registered handler for work mode: {}", mode);
    }

    /**
     * Get the handler for a specific work mode.
     *
     * @param mode the work mode
     * @return the handler, or null if not registered
     */
    public TerrainToolHandler get(WorkMode mode) {
        return handlers.get(mode);
    }

    /**
     * Get the handler for a specific work mode, wrapped in Optional.
     *
     * @param mode the work mode
     * @return Optional containing the handler, or empty if not registered
     */
    public Optional<TerrainToolHandler> getOptional(WorkMode mode) {
        return Optional.ofNullable(handlers.get(mode));
    }

    /**
     * Check if a handler is registered for a specific work mode.
     *
     * @param mode the work mode
     * @return true if a handler is registered, false otherwise
     */
    public boolean hasHandler(WorkMode mode) {
        return handlers.containsKey(mode);
    }

    /**
     * Clear all registered handlers.
     * Primarily for testing purposes.
     */
    public void clear() {
        handlers.clear();
        LOGGER.debug("Cleared all registered handlers");
    }

    /**
     * Get the number of registered handlers.
     *
     * @return the number of registered handlers
     */
    public int size() {
        return handlers.size();
    }
}

package com.pushdozer.ui.selection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MultiSelectStrategy<T> implements SelectionStrategy<T> {
    private final Set<T> selected = new HashSet<>();

    public MultiSelectStrategy() {
    }

    public MultiSelectStrategy(Collection<T> initial) {
        if (initial != null) {
            selected.addAll(initial);
        }
    }

    public Set<T> snapshot() {
        return new HashSet<>(selected);
    }

    @Override
    public boolean isSelected(T item) {
        return selected.contains(item);
    }

    @Override
    public void toggle(T item) {
        if (selected.contains(item)) {
            selected.remove(item);
        } else {
            selected.add(item);
        }
    }

    @Override
    public void selectAll(Collection<T> items) {
        selected.clear();
        selected.addAll(items);
    }

    @Override
    public void clearAll() {
        selected.clear();
    }

    @Override
    public int selectedCount() {
        return selected.size();
    }

    @Override
    public void select(T item) {
        selected.add(item);
    }

    @Override
    public void deselect(T item) {
        selected.remove(item);
    }
}

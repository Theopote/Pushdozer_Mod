package com.pushdozer.ui.selection;

import java.util.Collection;

public class SingleSelectStrategy<T> implements SelectionStrategy<T> {
    private T selected;

    @Override
    public boolean isSelected(T item) {
        return selected == item;
    }

    @Override
    public void toggle(T item) {
        selected = (selected == item) ? null : item;
    }

    @Override
    public void selectAll(Collection<T> items) {
        if (!items.isEmpty()) {
            selected = items.iterator().next();
        }
    }

    @Override
    public void clearAll() {
        selected = null;
    }

    @Override
    public int selectedCount() {
        return selected == null ? 0 : 1;
    }

    @Override
    public void select(T item) {
        selected = item;
    }

    @Override
    public void deselect(T item) {
        if (selected == item) {
            selected = null;
        }
    }

    public T getSelected() {
        return selected;
    }

    @Override
    public void toggleCategory(java.util.List<T> categoryItems) {
        if (categoryItems.isEmpty()) {
            return;
        }
        if (categoryItems.contains(selected)) {
            selected = null;
        } else {
            selected = categoryItems.getFirst();
        }
    }
}

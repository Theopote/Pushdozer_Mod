package com.pushdozer.ui.selection;

import java.util.Collection;
import java.util.List;

/**
 * 选择策略：单选/多选等行为注入点。
 */
public interface SelectionStrategy<T> {
    boolean isSelected(T item);

    void toggle(T item);

    void selectAll(Collection<T> items);

    void clearAll();

    int selectedCount();

    default void toggleCategory(List<T> categoryItems) {
        if (categoryItems.isEmpty()) {
            return;
        }
        boolean allSelected = categoryItems.stream().allMatch(this::isSelected);
        if (allSelected) {
            categoryItems.forEach(this::deselect);
        } else {
            categoryItems.forEach(this::select);
        }
    }

    void select(T item);

    void deselect(T item);
}

package com.pushdozer.ui.selection;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectionCategory<T> {
    private final String translationKey;
    private final int priority;
    private final List<T> items = new ArrayList<>();
    private final Set<String> addedIds = new HashSet<>();

    public SelectionCategory(String translationKey, int priority) {
        this.translationKey = translationKey;
        this.priority = priority;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public int getPriority() {
        return priority;
    }

    public List<T> getItems() {
        return items;
    }

    public Text getTranslatedName() {
        return Text.translatable(translationKey);
    }

    public void addItem(T item, String uniqueId) {
        if (uniqueId != null && addedIds.add(uniqueId)) {
            items.add(item);
        }
    }

    public static <T> void sortByPriority(List<SelectionCategory<T>> categories) {
        categories.sort(Comparator.comparingInt(SelectionCategory::getPriority));
    }
}

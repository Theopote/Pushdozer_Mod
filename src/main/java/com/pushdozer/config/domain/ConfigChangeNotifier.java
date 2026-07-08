package com.pushdozer.config.domain;

@FunctionalInterface
public interface ConfigChangeNotifier {
    void onConfigChanged();
}

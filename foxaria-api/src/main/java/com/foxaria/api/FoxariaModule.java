package com.foxaria.api;

import java.util.List;

public interface FoxariaModule {

    String id();

    default List<String> dependencies() {
        return List.of();
    }

    default List<MigrationScript> migrations() {
        return List.of();
    }

    void start(ModuleContext context) throws Exception;

    void stop() throws Exception;
}

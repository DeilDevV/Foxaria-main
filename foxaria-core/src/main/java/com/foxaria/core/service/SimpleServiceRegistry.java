package com.foxaria.core.service;

import com.foxaria.api.service.ServiceRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleServiceRegistry implements ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T require(Class<T> type) {
        Object value = services.get(type);
        if (value == null) {
            throw new IllegalStateException("Required service not registered: " + type.getName());
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T optional(Class<T> type) {
        return (T) services.get(type);
    }
}

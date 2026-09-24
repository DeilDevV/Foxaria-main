package com.foxaria.api.service;

public interface ServiceRegistry {

    <T> void register(Class<T> type, T instance);

    <T> T require(Class<T> type);

    <T> T optional(Class<T> type);
}

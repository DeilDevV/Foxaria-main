package com.foxaria.api;

public record MigrationScript(int version, String description, String resourcePath) {
}

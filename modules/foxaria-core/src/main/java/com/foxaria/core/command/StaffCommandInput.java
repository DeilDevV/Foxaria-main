package com.foxaria.core.command;

import java.util.Arrays;

public record StaffCommandInput(String[] args, boolean silent) {

    public static StaffCommandInput parse(String[] rawArgs) {
        if (rawArgs.length == 0) {
            return new StaffCommandInput(rawArgs, false);
        }
        if ("-s".equalsIgnoreCase(rawArgs[rawArgs.length - 1])) {
            return new StaffCommandInput(Arrays.copyOf(rawArgs, rawArgs.length - 1), true);
        }
        return new StaffCommandInput(rawArgs, false);
    }
}

package com.foxaria.admin;

public final class AdminState {

    private volatile boolean maintenance;

    public boolean maintenance() {
        return maintenance;
    }

    public void maintenance(boolean maintenance) {
        this.maintenance = maintenance;
    }
}

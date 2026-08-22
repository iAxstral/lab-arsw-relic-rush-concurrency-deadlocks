package edu.eci.arsw.relicrush.model;

/**
 * Exclusive resource used to craft a relic. The station itself is the monitor.
 */
public final class ForgeStation {
    private final int id;
    private final String name;
    private volatile boolean busy;

    public ForgeStation(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name + "(#" + id + ")";
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public boolean isBusy() {
        return busy;
    }
}

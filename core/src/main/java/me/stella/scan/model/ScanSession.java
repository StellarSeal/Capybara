package me.stella.scan.model;

import java.io.File;
import java.util.UUID;

/** One running (or settled) scan: its job, owner, live progress and outcome. */
public final class ScanSession {

    public enum State { RUNNING, COMPLETED, FAILED }

    private final int id;
    private final UUID ownerId;
    private final String ownerName;
    private final ScanJob job;
    private final ScanProgress progress = new ScanProgress();
    private volatile State state = State.RUNNING;
    private volatile File report;
    private volatile Throwable failure;

    public ScanSession(int id, UUID ownerId, String ownerName, ScanJob job) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.job = job;
    }

    public void complete(File report) {
        this.report = report;
        this.state = State.COMPLETED;
    }

    public void fail(Throwable failure) {
        this.failure = failure;
        this.state = State.FAILED;
    }

    public int id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public ScanJob job() {
        return job;
    }

    public ScanProgress progress() {
        return progress;
    }

    public State state() {
        return state;
    }

    public boolean running() {
        return state == State.RUNNING;
    }

    public File report() {
        return report;
    }

    public Throwable failure() {
        return failure;
    }
}

package uk.co.community.imagebrowser.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks progress of the single admin "reload" operation (OutputSyncService.sync()
 * followed by AviationImportService.reimport(), both run on a background thread by
 * AdminReloadService) so the UI can poll a percentage instead of blocking on one
 * long request. Reload is single-flight — tryStart() rejects a second reload while
 * one is running — so one shared mutable state is enough; no per-request tracking.
 */
@Service
public class ImportProgressService {

    public enum Status { IDLE, RUNNING, SUCCESS, ERROR }

    private final AtomicBoolean           running   = new AtomicBoolean(false);
    private final AtomicReference<Status> status    = new AtomicReference<>(Status.IDLE);
    private final AtomicReference<String> stage     = new AtomicReference<>("");
    private final AtomicInteger           total     = new AtomicInteger(0);
    private final AtomicInteger           completed = new AtomicInteger(0);
    private final AtomicReference<String> message   = new AtomicReference<>("");

    /** Claims the single reload slot; false means one is already running. */
    public boolean tryStart() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        status.set(Status.RUNNING);
        stage.set("Starting...");
        total.set(0);
        completed.set(0);
        message.set("");
        return true;
    }

    /** Starts a new phase of the reload (e.g. "Copying files", "Importing records"). */
    public void beginStage(String stageName, int totalItems) {
        stage.set(stageName);
        total.set(Math.max(totalItems, 0));
        completed.set(0);
    }

    /** Reports how many items of the current stage's total are done. */
    public void advance(int completedItems) {
        completed.set(completedItems);
    }

    public void succeed(String finalMessage) {
        message.set(finalMessage);
        status.set(Status.SUCCESS);
        running.set(false);
    }

    public void fail(String errorMessage) {
        message.set(errorMessage);
        status.set(Status.ERROR);
        running.set(false);
    }

    public Snapshot snapshot() {
        int t = total.get();
        int c = completed.get();
        int percent = t <= 0 ? 0 : Math.min(100, (c * 100) / t);
        return new Snapshot(status.get(), stage.get(), percent, message.get());
    }

    public record Snapshot(Status status, String stage, int percent, String message) {}
}

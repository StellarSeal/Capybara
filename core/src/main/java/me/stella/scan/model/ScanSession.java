package me.stella.scan.model;

import java.io.File;
import java.util.UUID;

/** One running or settled scan session: its job, owner, live progress and outcome. */
public final class ScanSession {

   public enum State {

      RUNNING,
      COMPLETED,
      CANCELLED,
      FAILED
   }

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
      if (state == State.RUNNING) {
         this.report = report;
         this.state = State.COMPLETED;
      }
   }

   public void cancel() {
      progress.cancel();
      if (state == State.RUNNING) {
         this.state = State.CANCELLED;
      }
   }

   public void fail(Throwable failure) {
      if (state == State.RUNNING) {
         this.failure = failure;
         this.state = State.FAILED;
      }
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

   public boolean cancelled() {
      return state == State.CANCELLED || progress.isCancelled();
   }

   public File report() {
      return report;
   }

   public Throwable failure() {
      return failure;
   }
}

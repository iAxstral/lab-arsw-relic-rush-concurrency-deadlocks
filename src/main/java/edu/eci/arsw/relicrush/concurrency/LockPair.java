package edu.eci.arsw.relicrush.concurrency;

import edu.eci.arsw.relicrush.model.ForgeStation;

/**
 * Starter implementation intentionally contains a deadlock risk.
 *
 * Students: do NOT replace this with one global lock. Preserve concurrency
 * between forge operations that use disjoint stations.
 */
public final class LockPair {

    private LockPair() {
    }

    public static void withBoth(ForgeStation a, ForgeStation b, Runnable action) {
        // Deadlock prevention: always acquire the lower-id station first, so
        // two threads racing for the same pair never wait on each other.
        ForgeStation first = a.id() < b.id() ? a : b;
        ForgeStation second = a.id() < b.id() ? b : a;
        synchronized (first) {
            // This small delay makes the deadlock easier to reproduce in the starter.
            sleepQuietly(2);
            synchronized (second) {
                // Busy flags are observability only (read by the GUI); they do
                // not participate in the locking/ordering strategy above.
                first.setBusy(true);
                second.setBusy(true);
                try {
                    action.run();
                } finally {
                    second.setBusy(false);
                    first.setBusy(false);
                }
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void withEither(ForgeStation a, ForgeStation b, Runnable action) {
        // Fix it using a deterministic ordering strategy (or justify another
        // deadlock-prevention approach) while preserving fine-grained locking.
        ForgeStation first = a.id() < b.id() ? a : b;
        ForgeStation second = a.id() < b.id() ? b : a;
        synchronized (first) {
            first.setBusy(true);
            synchronized(second){
                second.setBusy(true);
                try {
                    action.run();
                } finally {
                    first.setBusy(false);
                    second.setBusy(false);
                }
            }
            
        }
    }
}

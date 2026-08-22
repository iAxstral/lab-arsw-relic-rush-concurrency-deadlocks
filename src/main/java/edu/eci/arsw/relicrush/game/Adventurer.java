package edu.eci.arsw.relicrush.game;

import edu.eci.arsw.relicrush.concurrency.ForgeLedger;
import edu.eci.arsw.relicrush.concurrency.GameControl;
import edu.eci.arsw.relicrush.concurrency.LockPair;
import edu.eci.arsw.relicrush.model.ForgeEvent;
import edu.eci.arsw.relicrush.model.ForgeStation;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * One player = one platform thread. This is intentional for the lab.
 */
public final class Adventurer extends Thread {
    private final int playerId;
    private final List<ForgeStation> stations;
    private final ForgeLedger ledger;
    private final CyclicBarrier roundStart;
    private final CyclicBarrier roundEnd;
    private final int rounds;
    private final SplittableRandom random;
    private final GameControl control;

    // volatile: read cross-thread by the GUI's polling timer, written only
    // by this thread (as before) - no change to the update logic itself.
    private volatile int score;

    public Adventurer(
            int playerId,
            List<ForgeStation> stations,
            ForgeLedger ledger,
            CyclicBarrier roundStart,
            CyclicBarrier roundEnd,
            int rounds,
            GameControl control) {
        super("adventurer-" + playerId);
        this.playerId = playerId;
        this.stations = stations;
        this.ledger = ledger;
        this.roundStart = roundStart;
        this.roundEnd = roundEnd;
        this.rounds = rounds;
        this.control = control;
        this.random = new SplittableRandom(1000L + playerId);
    }

    public int playerId() {
        return playerId;
    }

    public int score() {
        return score;
    }

    @Override
    public void run() {
        try {
            for (int round = 1; round <= rounds; round++) {
                if (control.isStopped()) {
                    return;
                }
                control.awaitIfPaused();
                roundStart.await();
                playTurn(round);
                roundEnd.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException e) {
            // Coordinator broke the barrier because the game was stopped.
        }
    }

    private void playTurn(int round) {
        int firstIndex = random.nextInt(stations.size());
        int secondIndex;
        do {
            secondIndex = random.nextInt(stations.size());
        } while (secondIndex == firstIndex);

        ForgeStation first = stations.get(firstIndex);
        ForgeStation second = stations.get(secondIndex);

        LockPair.withBoth(first, second, () -> {
            // score is only written by this player's thread.
            score++;
            ledger.record(new ForgeEvent(
                    round,
                    getName(),
                    first.name(),
                    second.name(),
                    score));
        });
    }
}

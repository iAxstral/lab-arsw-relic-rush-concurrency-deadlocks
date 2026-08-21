# ARSW Lab 3 - Relic Rush - Delivery Report

## Team

| Student | ID | GitHub |
|---|---|---|
| Isaac Burgos | | Isaac1805BC |
| Tomas | | |
| Javier Romero | | |

Repository: `URL`

Final commit: `SHA`

## 1. Baseline observations

- Command(s) executed:
- What happened?
- Was the round invariant always preserved?
- Did the game stop unexpectedly?

Evidence:

```text
PASTE RELEVANT OUTPUT
```

## 2. Coordination analysis

Explain the responsibility of both barriers:

- `roundStart`: this is the "start gate" (Scenario A). Every `Adventurer` thread calls `roundStart.await()` at the top of its round loop and blocks there. The coordinator (`GameEngine.run`) also calls `roundStart.await()` once per round. Because a `CyclicBarrier` releases all parties only when the last one arrives, no adventurer can begin `playTurn()` for round *N* until the coordinator has explicitly opened that round. This turns an otherwise free-running set of threads into a lock-step round structure: round *N* cannot start while round *N-1* is still being read/printed by the coordinator, and no adventurer can "peek ahead" into a future round.
- `roundEnd`: this is "round completion" (Scenario B). Every adventurer calls `roundEnd.await()` right after finishing `playTurn()` (i.e., after it has already updated its score and recorded its `ForgeEvent` in the `ForgeLedger`). The coordinator blocks on the same barrier before it reads the scoreboard/ledger for `printRoundSnapshot`. This guarantees the coordinator never observes a round's state while any adventurer is still mid-craft — it only reads once every worker has crossed the barrier, i.e., once every worker for that round is done.

Why is `Thread.sleep(...)` not a valid replacement for a barrier?

`Thread.sleep(...)` coordinates threads through a *guessed duration*, not through an actual happens-before relationship with the other threads' progress. It has no knowledge of how many adventurers have actually finished their turn, so it is simultaneously:

- **Unsafe**: if a sleep is too short (e.g., under load, GC pause, or scheduling delay), the coordinator can read the scoreboard/ledger while some adventurers are still inside `LockPair.withBoth(...)`, producing a torn read and a `BROKEN` invariant even with a correct `ForgeLedger`.
- **Wasteful when it "works"**: to be safe under worst-case scheduling you must sleep for much longer than the typical turn duration, which serializes rounds behind a fixed, pessimistic delay instead of releasing the coordinator the instant the last adventurer actually finishes.
- **Non-portable**: the "safe" sleep duration depends on machine load, thread count, and JIT warm-up, so a value tuned for 8 adventurers is not guaranteed to be sufficient for 128.

A `CyclicBarrier`, in contrast, encodes an exact rendezvous condition (`N` parties must call `await()`) enforced by the JMM, with no arbitrary timing assumption.

What memory-consistency benefit do you obtain by reading the snapshot after the barrier?

`CyclicBarrier.await()` establishes a happens-before edge: every action performed by a thread *before* it calls `await()` happens-before the actions performed by any thread *after* it returns from the corresponding `await()` (per `java.util.concurrent.CyclicBarrier` Javadoc / JMM). Concretely, every write an adventurer makes to `score` and every `ledger.record(...)` call it performs during `playTurn()` happens-before the coordinator's calls to `Adventurer::score`, `ledger.totalCrafted()`, and `ledger.eventCount()` in `printRoundSnapshot`, because those reads occur only after `roundEnd.await()` returns on the coordinator side. This is what lets the coordinator safely read plain (non-volatile) fields like `Adventurer.score` without any additional synchronization: the barrier itself is the memory fence. Without it, there would be no guarantee the coordinator's thread ever observes the adventurers' writes (visibility), independent of any interleaving/race issue.

## 3. Thread-safety problems

| Shared state | Problem | Invariant at risk | Solution | Why this solution? |
|---|---|---|---|---|
| | | | | |
| | | | | |

## 4. Deadlock diagnosis

### 4.1 Evidence

```text
PASTE DeadlockProbe OR jcmd/jstack EVIDENCE
```

### 4.2 Coffman conditions in Relic Rush

- Mutual exclusion:
- Hold and wait:
- No preemption:
- Circular wait:

### 4.3 Wait-for graph

Describe or add a diagram.

### 4.4 Fix

What condition did you break?

How did you preserve concurrency between independent forge operations?

## 5. Verification

| Players | Stations | Rounds | Deadlock? | Invariant result |
|---:|---:|---:|---|---|
| 8 | 6 | 50 | | |
| 32 | 8 | 100 | | |
| 128 | 8 | 100 | | |

## 6. Architectural trade-offs

Discuss:

- Correctness / reliability
- Performance / throughput
- Contention
- Maintainability
- Scalability

## 7. Mini ADR

### Context

### Decision

### Alternatives considered

### Consequences

### Evidence

## 8. Conclusions

1.
2.
3.

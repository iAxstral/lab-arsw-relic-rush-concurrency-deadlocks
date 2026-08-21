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

> Scope note: the full-game baseline (deadlock behavior of `RelicRushMain` / `DeadlockProbe`) is documented by the Part III/IV owner. This entry only covers the isolated ledger probe used for Part II.

- Command(s) executed:

```bash
mvn -q -DskipTests clean package
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe 64 5000
```

- What happened? With the starter `ForgeLedger`, `expected` writes were always higher than both `totalCrafted` and `eventCount`. Every run under-counted, and the shortfall differed from run to run (non-deterministic race).
- Was the round invariant always preserved? No. `totalCrafted != eventCount != expected` in every run.
- Did the game stop unexpectedly? No crash/exception, just silent lost updates.

Evidence (starter implementation, 3 consecutive runs, 64 workers x 5000 writes each = 320000 expected):

```text
expected=320000 totalCrafted=9368  eventCount=211826 invariant=BROKEN
expected=320000 totalCrafted=9487  eventCount=213772 invariant=BROKEN
expected=320000 totalCrafted=10461 eventCount=210941 invariant=BROKEN
```

`totalCrafted` lost ~97% of its increments and `eventCount` lost ~34% of its entries, which shows the two bugs manifest at very different severities: a plain `int++` is almost always clobbered under contention, while `ArrayList` occasionally survives a batch before an internal array-resize race drops or corrupts elements.

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
| `ForgeLedger.totalCrafted` (was `int`) | `int next = totalCrafted + 1; totalCrafted = next;` is a non-atomic read-modify-write. Concurrent adventurer threads can read the same stale value and overwrite each other's increment (lost update). | `sum of player scores == ForgeLedger.totalCrafted` | Replaced with `AtomicInteger`, updated via `incrementAndGet()` (a single CAS-based atomic operation). | `AtomicInteger` gives lock-free, wait-free atomicity for a single counter without introducing a monitor. It is strictly cheaper than `synchronized` for this specific op (no blocking, no context-switch risk) and does not couple the counter's contention to the list's contention. |
| `ForgeLedger.events` (was `ArrayList<ForgeEvent>`) | `ArrayList` is not designed for concurrent structural modification. Concurrent `add()` calls can race on the internal array/`size` field, silently dropping elements or throwing `ArrayIndexOutOfBoundsException`/`ConcurrentModificationException`. | `ForgeLedger.totalCrafted == number of ForgeEvent entries` | Replaced with `ConcurrentLinkedQueue<ForgeEvent>`, a lock-free (Michael-Scott) concurrent queue; `add()` is safe for any number of concurrent producers. | The list is write-heavy (every craft appends once) and read-rarely (only `eventCount()`/`snapshot()`, called once per round by the coordinator, off the hot path). `ConcurrentLinkedQueue` gives O(1) lock-free `add()`, which is far cheaper under contention than `CopyOnWriteArrayList` (O(n) copy per write — unacceptable here) or `Collections.synchronizedList` (blocking monitor on every write, effectively serializing all adventurers on this one field). |

Why is this preferable to synchronizing the entire game (e.g., one `synchronized` method wrapping both fields, or a lock around all of `record`)?

The two invariants only require that (a) each individual field is internally consistent under concurrent access, and (b) both fields are incremented exactly once per crafted relic. They do **not** require the counter update and the list append to be atomic *with respect to each other* — nothing in the game ever reads `totalCrafted` and `events` in a way that assumes they change as a single indivisible unit mid-round; they are only compared for equality by the coordinator, and only after every adventurer has already crossed `roundEnd` (which — per Part I — is itself a memory-consistency fence). So each field can be made independently thread-safe with the cheapest tool that fits its own access pattern, instead of forcing a single global lock around `record()` that would serialize every adventurer's craft on the ledger, turning `ForgeLedger` into a bottleneck shared by every station pair in the game. `LedgerRaceProbe` stress evidence below confirms both fields stay consistent under heavy concurrent writes without any coarse locking:

```text
expected=320000  totalCrafted=320000  eventCount=320000  invariant=OK   (64 workers  x 5000 writes)
expected=320000  totalCrafted=320000  eventCount=320000  invariant=OK   (repeated 3x)
expected=2560000 totalCrafted=2560000 eventCount=2560000 invariant=OK   (128 workers x 20000 writes)
```

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

1. The two `ForgeLedger` defects (non-atomic counter, non-thread-safe list) were reproduced deterministically with `LedgerRaceProbe` and fixed independently with `AtomicInteger` and `ConcurrentLinkedQueue`, without introducing any new lock.
2. The round barriers (`roundStart`, `roundEnd`) were already correct; they are the mechanism that makes it safe for the coordinator to read `score`/`totalCrafted`/`eventCount` with plain field reads after each round.
3. (Deadlock diagnosis/fix conclusion — pending Part III/IV/V owner.)

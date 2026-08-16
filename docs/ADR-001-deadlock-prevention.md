# ADR-001: Deadlock prevention strategy

## Context

LockPair.withBoth acquires two ForgeStation locks in the order the caller
gives them. If two adventurers ask for the same two stations but in
opposite order, each one grabs a different station and waits forever for
the other. We reproduced this with DeadlockProbe. We need every thread to
be able to finish, without going back to one single lock for the game.

## Decision

We order every ForgeStation by its id and always grab the smaller id
first, no matter what order the caller passed them in.

```java
public static void withBoth(ForgeStation a, ForgeStation b, Runnable action) {
    ForgeStation first = a.id() < b.id() ? a : b;
    ForgeStation second = a.id() < b.id() ? b : a;
    synchronized (first) {
        synchronized (second) {
            action.run();
        }
    }
}
```

Two threads wanting the same pair will always try the same one first, so
they can never wait on each other.

## Alternatives considered

- One global lock for the whole game. Rejected because it would block even
  players using different stations, killing the concurrency we need.
- tryLock with retries. Rejected because it adds complexity and could keep
  threads retrying without ever making progress.

## Quality attributes affected

- Correctness. Deadlock becomes impossible.
- Performance. Just one extra id comparison per lock, barely noticeable.
- Maintainability. The rule lives in one method, not repeated everywhere.
- Scalability. Only players wanting the exact same pair of stations wait
  on each other, everyone else still runs in parallel.

## Evidence

Before the fix, DeadlockProbe caught the deadlock on the first run. After
the fix, we ran it six times in a row and every time it printed
NO DEADLOCK DETECTED within 2 seconds.

## Consequences

Good: we prevent the deadlock instead of just detecting it, and the change
only touches LockPair. Bad: this only protects code that actually calls
LockPair.withBoth. New code grabbing two stations manually would skip this
protection.

## Risks

Locking three or more stations at once would need this idea to be
extended, for example sorting a list of stations by id first. It also
depends on station ids staying fixed and never repeating.
# ARSW Lab 3 - Relic Rush - Delivery Report

## Team

| Student | ID | GitHub |
|---|---|---|
| | | |
| | | |
| | | |

Repository: `https://github.com/iAxstral/lab-arsw-relic-rush-concurrency-deadlocks`

Final commit: `SHA`

## 1. Baseline observations

- Command(s) executed:
- What happened?
- Was the round invariant always preserved?
- Did the game stop unexpectedly?

Evidence:

\`\`\`text
DEADLOCK DETECTED
- probe-A-anvil-then-furnace waiting on edu.eci.arsw.relicrush.model.ForgeStation@50040f0c owned by probe-B-furnace-then-anvil
- probe-B-furnace-then-anvil waiting on edu.eci.arsw.relicrush.model.ForgeStation@65b3120a owned by probe-A-anvil-then-furnace
\`\`\`

## 2. Coordination analysis

Explain the responsibility of both barriers:

- `roundStart`:
- `roundEnd`:

Why is `Thread.sleep(...)` not a valid replacement for a barrier?

## 3. Thread-safety problems

| Shared state | Problem | Invariant at risk | Solution | Why this solution? |
|---|---|---|---|---|
| | | | | |
| | | | | |

## 4. Deadlock diagnosis

### 4.1 Evidence

- **Command:** `java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe`
- **Execution:** reproducido a la primera corrida (también confirmado en corridas repetidas antes del fix)

`DeadlockProbe` lanza dos hilos daemon: `probe-A` adquiere `anvil` y luego intenta `furnace`; `probe-B` adquiere `furnace` y luego intenta `anvil`. El `ThreadMXBean` de la JVM detecta el ciclo de espera y lo reporta directamente, sin necesidad de `jstack`/`jcmd` externo.

### 4.2 Coffman conditions in Relic Rush

- **Mutual exclusion:** cada `ForgeStation` es el monitor de sí misma — el `synchronized(first)`/`synchronized(second)` en `LockPair.withBoth()` garantiza que solo un hilo a la vez puede tener una estación dada.
- **Hold and wait:** en el `LockPair` original, el hilo que ya adquirió `first` el cual esta dentro del primer bloque `synchronized` queda bloqueado esperando `second` **sin soltar** `first` — retiene un recurso mientras espera otro.
- **No preemption:** ningún hilo puede ser forzado a liberar el lock de una `ForgeStation` que ya posee; el JVM/SO no puede "quitárselo" para desbloquear la situación — solo el propio hilo dueño puede soltarlo al salir del bloque `synchronized`, y eso nunca ocurre porque está bloqueado.
- **Circular wait:** `probe-A` pide `anvil` y luego espera `furnace` el cual lo tiene B; `probe-B` pide `furnace` y luego espera `anvil` el cual tiene A — el ciclo se cierra: A → Furnace → B → Anvil → A.


### 4.3 Wait-for graph

\`\`\`
   espera            espera
 A ────────► Furnace ◄──────── B
 ▲                              │
 │           posee              │ posee
 └──────── Anvil ◄──────────────┘
\`\`\`

`probe-A` posee `Anvil` y espera `Furnace` (que posee `probe-B`); `probe-B` posee `Furnace` y espera `Anvil` (que posee `probe-A`). El ciclo cerrado `A → Furnace → B → Anvil → A` es la firma característica de un deadlock — mientras exista, ningún hilo involucrado puede progresar.

### 4.4 Fix

What condition did you break?

> **Circular wait (espera circular).** Se definió un orden total determinista sobre las `ForgeStation` (por `id()`) y se modificó `LockPair.withBoth()` para que, sin importar en qué orden lleguen los dos parámetros, siempre se calcule cuál tiene el `id()` menor y se adquiera esa primero:
>
> \`\`\`java
> public static void withBoth(ForgeStation a, ForgeStation b, Runnable action) {
>     ForgeStation first = a.id() < b.id() ? a : b;
>     ForgeStation second = a.id() < b.id() ? b : a;
>     synchronized (first) {
>         synchronized (second) {
>             action.run();
>         }
>     }
> }
> \`\`\`
>
> Con esto, dos hilos que compiten por el mismo par de estaciones **siempre** intentan adquirir primero la de menor ID — es matemáticamente imposible que se forme un ciclo de espera, sin importar en qué orden cada jugador haya "pedido" lógicamente las estaciones.

How did you preserve concurrency between independent forge operations?

> El orden global solo afecta a los hilos que compiten por el **mismo par exacto** de estaciones. Dos jugadores que necesitan pares de estaciones completamente distintos (sin ninguna en común) nunca esperan entre sí — cada `synchronized` sigue siendo de grano fino, uno por `ForgeStation`, no un lock global para todo el juego. Se verificó que, tras el fix, `DeadlockProbe` reporta `NO DEADLOCK DETECTED within 2 seconds.` de forma consistente en 6 corridas seguidas.

![alt text](image.png)

\`\`\`text
NO DEADLOCK DETECTED within 2 seconds.
If you already fixed LockPair, this is the expected result.
\`\`\`
(6/6 corridas consecutivas, todas con este resultado)

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

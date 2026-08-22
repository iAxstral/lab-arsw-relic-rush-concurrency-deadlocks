# Parte VI - Stress verification (version PowerShell para Windows)
# Corre esto DESDE LA RAIZ del repo (donde esta pom.xml), en tu maquina local.
# Requiere JDK 21 + Maven 3.9+ instalados y en el PATH (verifica con java -version / mvn -version).
#
# Uso (desde PowerShell, parado en la carpeta del proyecto):
#   .\verify_stress.ps1 | Tee-Object -FilePath docs\evidence\stress-run.txt
#
# Si PowerShell te bloquea la ejecucion de scripts la primera vez, corre antes (una sola vez):
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#
# El archivo docs\evidence\stress-run.txt queda listo para pegar directamente
# en la seccion 5 (Verification) de docs\REPORT.md.

$ErrorActionPreference = "Stop"

Write-Host "=============================================="
Write-Host "1) Build limpio + tests unitarios"
Write-Host "=============================================="
mvn -q clean test
mvn -q -DskipTests package

Write-Host ""
Write-Host "=============================================="
Write-Host "2) LedgerRaceProbe - thread-safety del ledger aislado"
Write-Host "=============================================="
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe 64 5000
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe 128 20000

Write-Host ""
Write-Host "=============================================="
Write-Host "3) DeadlockProbe - debe reportar NO DEADLOCK, 6 corridas seguidas"
Write-Host "=============================================="
for ($i = 1; $i -le 6; $i++) {
    Write-Host "--- corrida $i ---"
    java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
}

Write-Host ""
Write-Host "=============================================="
Write-Host "4) InvariantProbe - juego completo end-to-end, 3 configuraciones"
Write-Host "   (segun README seccion 13 / Parte VI)"
Write-Host "=============================================="
Write-Host "--- 8 players / 6 stations / 50 rounds ---"
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 8 6 50

Write-Host "--- 32 players / 8 stations / 100 rounds ---"
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 32 8 100

Write-Host "--- 128 players / 8 stations / 100 rounds ---"
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 128 8 100

Write-Host ""
Write-Host "=============================================="
Write-Host "LISTO. Revisa que:"
Write-Host " - todas las rondas impresas digan invariant=OK"
Write-Host " - los 3 InvariantProbe terminen con 'FINAL SCORE' (el proceso no se cuelga)"
Write-Host " - los 6 DeadlockProbe digan 'NO DEADLOCK DETECTED within 2 seconds.'"
Write-Host "Si algo falla, ese comando+salida es la evidencia de un bug real que hay que reabrir."
Write-Host "=============================================="
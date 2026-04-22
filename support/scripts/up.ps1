$supportDir = Split-Path -Parent $PSScriptRoot
Set-Location $supportDir
docker compose up -d
Write-Host "Waiting for database to be healthy..."
while ((docker inspect -f {{.State.Health.Status}} pgcheck-mcp-db) -ne "healthy") {
    Start-Sleep -Seconds 1
}
Write-Host "Database is ready!"

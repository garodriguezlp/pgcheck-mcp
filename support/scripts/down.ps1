$supportDir = Split-Path -Parent $PSScriptRoot
Set-Location $supportDir
docker compose down -v

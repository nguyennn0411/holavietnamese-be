param([switch]$StartBackend)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath '.env.mysql')) {
        $appPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
        $rootPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
        $jwtSecret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
        $settings = (Get-Content -LiteralPath '.env.mysql.example' -Raw).
            Replace('replace-with-your-app-password', $appPassword).
            Replace('replace-with-a-different-root-password', $rootPassword).
            Replace('replace-with-random-jwt-key', $jwtSecret)
        Set-Content -LiteralPath '.env.mysql' -Value $settings -Encoding utf8NoBOM
        Write-Host 'Created .env.mysql with generated local passwords.'
    }
    if ($StartBackend) {
        docker compose --env-file .env.mysql -f deployment/compose.yml up -d --build --wait
    } else {
        docker compose --env-file .env.mysql -f deployment/compose.yml up -d --wait mysql
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. Check Docker Desktop (Linux containers) and container logs.' }
    Write-Host 'MySQL is ready. Use docker compose --env-file .env.mysql -f deployment/compose.yml logs backend to inspect backend startup.'
} finally {
    Pop-Location
}

param([switch]$StartBackend)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env.mysql')) {
        $appPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
        $rootPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
        $settings = (Get-Content -LiteralPath '.env.mysql.example' -Raw).
            Replace('replace-with-your-app-password', $appPassword).
            Replace('replace-with-a-different-root-password', $rootPassword)
        Set-Content -LiteralPath '.env.mysql' -Value $settings -Encoding utf8NoBOM
        Write-Host 'Created .env.mysql with generated local passwords.'
    }
    if ($StartBackend) {
        docker compose --env-file .env.mysql -f compose.yml up -d --build --wait
    } else {
        docker compose --env-file .env.mysql -f compose.yml up -d --wait mysql
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. Check Docker Desktop (Linux containers) and container logs.' }
    Write-Host 'MySQL is ready. Use docker compose --env-file .env.mysql logs backend to inspect backend startup.'
} finally {
    Pop-Location
}

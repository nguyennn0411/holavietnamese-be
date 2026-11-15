param([switch]$StartBackend)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

function New-HexSecret([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [BitConverter]::ToString($bytes).Replace('-', '')
}

Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) {
        $appPassword = New-HexSecret 24
        $rootPassword = New-HexSecret 24
        $jwtSecret = New-HexSecret 48
        $settings = (Get-Content -LiteralPath '.env.example' -Raw).
            Replace('change-me-local-app-password', $appPassword).
            Replace('change-me-different-root-password', $rootPassword).
            Replace('replace-with-a-long-random-secret-at-least-32-bytes', $jwtSecret)
        [IO.File]::WriteAllText((Join-Path $repoRoot '.env'), $settings, $utf8WithoutBom)
        Write-Host 'Created .env with generated local passwords.'
    }
    if ($StartBackend) {
        docker compose --env-file .env -f deployment/compose.yml up -d --build --wait
    } else {
        docker compose --env-file .env -f deployment/compose.yml up -d --wait mysql
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. Check Docker Desktop (Linux containers) and container logs.' }
    Write-Host 'MySQL is ready. Use docker compose --env-file .env -f deployment/compose.yml logs backend to inspect backend startup.'
} finally {
    Pop-Location
}

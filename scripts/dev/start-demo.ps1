param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath '.env.mysql')) { & (Join-Path $PSScriptRoot 'start-mysql.ps1') }
    $settings = Get-Content -LiteralPath '.env.mysql' -Raw
    if ($settings -notmatch '(?m)^APP_DEMO_PASSWORD=.+$') {
        $demoPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(12))
        Add-Content -LiteralPath '.env.mysql' -Value "`nAPP_DEMO_PASSWORD=$demoPassword" -Encoding utf8NoBOM
    }
    $dockerArgs = @('compose', '--env-file', '.env.mysql', '-f', 'deployment/compose.yml', '-f', 'deployment/compose.demo.yml', 'up', '-d', '--wait')
    if (-not $SkipBuild) { $dockerArgs += '--build' }
    & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. Inspect the backend container logs.' }
    Write-Host 'Demo started. On an empty database it creates learner@hola.test and admin@hola.test.'
    Write-Host 'Read APP_DEMO_PASSWORD in .env.mysql locally. Existing databases and passwords are not reset.'
    Write-Host 'Frontend: enable VITE_DEMO_INTEGRATIONS=true in .env.local, then npm run dev.'
} finally { Pop-Location }

param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { & (Join-Path $PSScriptRoot 'start-mysql.ps1') }
    $dockerArgs = @('compose', '--env-file', '.env', '-f', 'deployment/compose.yml', '-f', 'deployment/compose.demo.yml', 'up', '-d', '--wait')
    if (-not $SkipBuild) { $dockerArgs += '--build' }
    & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. Inspect the backend container logs.' }
    Write-Host 'Demo started. Missing admin, content, and learner accounts are created automatically.'
    Write-Host 'Default usernames: admin, content, learner. Existing accounts are not modified.'
    Write-Host 'Frontend: enable VITE_DEMO_INTEGRATIONS=true in .env.local, then npm run dev.'
} finally { Pop-Location }

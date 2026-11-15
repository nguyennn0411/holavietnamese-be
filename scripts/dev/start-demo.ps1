param([switch]$SkipBuild)
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
    if (-not (Test-Path -LiteralPath '.env')) { & (Join-Path $PSScriptRoot 'start-mysql.ps1') }
    $settings = Get-Content -LiteralPath '.env' -Raw
    if ($settings -notmatch '(?m)^APP_DEMO_PASSWORD=(?!replace-with-).+$') {
        $demoPassword = New-HexSecret 12
        if ($settings -match '(?m)^APP_DEMO_PASSWORD=.*$') {
            $settings = [regex]::Replace($settings, '(?m)^APP_DEMO_PASSWORD=.*$', "APP_DEMO_PASSWORD=$demoPassword")
            [IO.File]::WriteAllText((Join-Path $repoRoot '.env'), $settings, $utf8WithoutBom)
        } else {
            [IO.File]::AppendAllText((Join-Path $repoRoot '.env'), "`nAPP_DEMO_PASSWORD=$demoPassword`n", $utf8WithoutBom)
        }
    }
    $dockerArgs = @('compose', '--env-file', '.env', '-f', 'deployment/compose.yml', '-f', 'deployment/compose.demo.yml', 'up', '-d', '--wait')
    if (-not $SkipBuild) { $dockerArgs += '--build' }
    & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. Inspect the backend container logs.' }
    Write-Host 'Demo started. On an empty database it creates learner@hola.test and admin@hola.test.'
    Write-Host 'Read APP_DEMO_PASSWORD in .env locally. Existing databases and passwords are not reset.'
    Write-Host 'Frontend: enable VITE_DEMO_INTEGRATIONS=true in .env.local, then npm run dev.'
} finally { Pop-Location }

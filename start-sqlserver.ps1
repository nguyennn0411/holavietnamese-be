param(
    [switch]$StartBackend,
    [string]$MavenCommand = 'mvn'
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env.sqlserver')) {
        throw 'Create .env.sqlserver from .env.sqlserver.example and set a strong password first.'
    }
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath '.env.sqlserver') {
        if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
    }
    if (-not $settings['MSSQL_SA_PASSWORD']) { throw 'MSSQL_SA_PASSWORD is required in .env.sqlserver.' }
    docker compose --env-file .env.sqlserver -f compose.sqlserver.yml up -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. Start Docker Desktop (Linux containers) and retry.' }
    $initContainer = docker compose --env-file .env.sqlserver -f compose.sqlserver.yml ps --all --quiet sqlserver-init
    if ($LASTEXITCODE -ne 0 -or -not $initContainer) { throw 'Database initialization container was not found.' }
    $initExitCode = docker wait $initContainer
    if ($LASTEXITCODE -ne 0 -or "$initExitCode".Trim() -ne '0') { throw 'SQL Server database initialization failed.' }
    Write-Host 'SQL Server is ready. Database: hola_vietnamese.'
    if ($StartBackend) {
        $previous = @{}
        foreach ($key in @('SPRING_PROFILES_ACTIVE', 'MSSQL_SA_PASSWORD', 'SQLSERVER_URL')) {
            $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        }
        try {
            $env:SPRING_PROFILES_ACTIVE = 'sqlserver'
            $env:MSSQL_SA_PASSWORD = $settings['MSSQL_SA_PASSWORD']
            $port = if ($settings['MSSQL_PORT']) { $settings['MSSQL_PORT'] } else { '14330' }
            $env:SQLSERVER_URL = "jdbc:sqlserver://localhost:$port;databaseName=hola_vietnamese;encrypt=true;trustServerCertificate=true"
            & $MavenCommand -DskipTests package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
            java -jar target/backend-0.0.1-SNAPSHOT.jar
            if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error.' }
        } finally {
            foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
        }
    }
} finally {
    Pop-Location
}

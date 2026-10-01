param([string]$MavenCommand = 'mvn')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env.mysql')) { throw 'Run ./start-mysql.ps1 first.' }
    $taskUserLine = Get-Content -LiteralPath '.env.mysql' | Where-Object { $_ -match '^MYSQL_USER=' } | Select-Object -First 1
    $taskUser = if ($taskUserLine) { $taskUserLine.Substring(11).Trim() } else { 'hola' }
    if ($taskUser -notmatch '^[a-zA-Z0-9_]+$') { throw 'Test setup requires an alphanumeric MYSQL_USER.' }
    $taskSql = "CREATE DATABASE IF NOT EXISTS hola_features_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci; GRANT ALL ON hola_features_test.* TO '$taskUser'@'%';"
    $taskSql | docker compose --env-file .env.mysql exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare isolated MySQL test database.' }
    & $MavenCommand clean test '-Dspring.profiles.active=mysql-test'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL tests failed.' }
} finally {
    Pop-Location
}

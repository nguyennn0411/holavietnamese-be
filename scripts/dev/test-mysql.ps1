param([string]$MavenCommand = 'mvn')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { & (Join-Path $PSScriptRoot 'start-mysql.ps1') }
    docker compose --env-file .env -f deployment/compose.yml up -d --wait mysql
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start the isolated MySQL test dependency.' }
    $taskUserLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^MYSQL_USER=' } | Select-Object -First 1
    $taskUser = if ($taskUserLine) { $taskUserLine.Substring(11).Trim() } else { 'hola' }
    if ($taskUser -notmatch '^[a-zA-Z0-9_]+$') { throw 'Test setup requires an alphanumeric MYSQL_USER.' }
    $taskSql = "DROP DATABASE IF EXISTS hola_vietnamese_test; DROP DATABASE IF EXISTS hola_vietnamese_site_content_test; CREATE DATABASE hola_vietnamese_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci; CREATE DATABASE hola_vietnamese_site_content_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci; GRANT ALL ON hola_vietnamese_test.* TO '$taskUser'@'%'; GRANT ALL ON hola_vietnamese_site_content_test.* TO '$taskUser'@'%';"
    $taskSql | docker compose --env-file .env -f deployment/compose.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare isolated MySQL test databases.' }
    & $MavenCommand clean test
    if ($LASTEXITCODE -ne 0) { throw 'MySQL tests failed.' }
} finally {
    Pop-Location
}

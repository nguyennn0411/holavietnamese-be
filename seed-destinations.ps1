param(
    [string]$BaseUrl = 'http://localhost:8080',
    [PSCredential]$Credential
)

# Populate an already running local backend without restarting it:
# ./seed-destinations.ps1 -Credential (Get-Credential)
# Existing slugs are skipped, including records edited or archived in Admin.
$ErrorActionPreference = 'Stop'
$baseUri = [Uri]$BaseUrl
if (-not $baseUri.IsAbsoluteUri -or $baseUri.Scheme -notin @('http', 'https')) {
    throw 'BaseUrl must be an absolute HTTP or HTTPS URL.'
}
if (-not $Credential) { $Credential = Get-Credential -Message 'Existing HolaVietnamese Admin account' }
$BaseUrl = $BaseUrl.TrimEnd('/')
$samples = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/demo/destinations.json') -Raw -Encoding utf8 | ConvertFrom-Json

$loginBody = @{ username = $Credential.UserName; password = $Credential.GetNetworkCredential().Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/token" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($loginBody))
if ($login.code -ne 1000 -or -not $login.result.authenticated -or -not $login.result.token) {
    throw 'Admin authentication failed.'
}
$headers = @{ Authorization = "Bearer $($login.result.token)" }

function Invoke-DestinationApi {
    param([string]$Method, [string]$Path, $Body)
    $arguments = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress))
    }
    $response = Invoke-RestMethod @arguments
    if ($response.code -ne 1000) { throw "Destination API failed: $($response.message)" }
    return $response.result
}

$existing = @(Invoke-DestinationApi -Method Get -Path '/api/admin/destinations')
$slugs = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($destination in $existing) { [void]$slugs.Add($destination.slug) }
$created = 0
$skipped = 0
foreach ($sample in $samples) {
    if ($slugs.Contains($sample.slug)) { $skipped++; continue }
    $destination = Invoke-DestinationApi -Method Post -Path '/api/admin/destinations' -Body $sample
    if (-not $destination.id) { throw "Missing ID after creating $($sample.slug)." }
    [void]$slugs.Add($destination.slug)
    $created++
}

$all = @(Invoke-DestinationApi -Method Get -Path '/api/admin/destinations')
[PSCustomObject]@{
    Created = $created
    Skipped = $skipped
    Total = $all.Count
    Statuses = @($all | Group-Object status | ForEach-Object { @{ Status = $_.Name; Count = $_.Count } })
    Regions = @($all | Group-Object region | ForEach-Object { @{ Region = $_.Name; Count = $_.Count } })
} | ConvertTo-Json -Depth 5

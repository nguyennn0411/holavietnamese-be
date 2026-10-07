$ErrorActionPreference = 'Stop'
# Use the installed Vietnamese voice explicitly; never fall back to an English voice.
$manifest = Get-Content -LiteralPath "$PSScriptRoot/../src/main/resources/learning/foundation-audio-manifest.json" -Raw -Encoding UTF8 | ConvertFrom-Json
$directory = [IO.Path]::GetFullPath("$PSScriptRoot/../src/main/resources/static/media/foundation")
[IO.Directory]::CreateDirectory($directory) | Out-Null
$voice = New-Object -ComObject SAPI.SpVoice
$token = New-Object -ComObject SAPI.SpObjectToken
$token.SetId('HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech_OneCore\Voices\Tokens\MSTTS_V110_viVN_An')
$voice.Voice = $token
$voice.Rate = -2
foreach ($item in $manifest) {
    $stream = New-Object -ComObject SAPI.SpFileStream
    $file = Join-Path $directory ($item.key + '.wav')
    try {
        $stream.Open($file, 3, $false)
        $voice.AudioOutputStream = $stream
        [void]$voice.Speak($item.text)
    } finally { $stream.Close() }
    if ((Get-Item -LiteralPath $file).Length -lt 1000) { throw "Empty audio: $file" }
}
Write-Output "Rendered $($manifest.Count) Vietnamese recordings."

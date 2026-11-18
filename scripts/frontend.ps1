param(
    [ValidateSet('start', 'stop')][string]$Action = 'start',
    [string]$NodePath = 'node.exe'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runDirectory = Join-Path $projectRoot 'target/local'
$statePath = Join-Path $runDirectory 'frontend.json'

if ($Action -eq 'stop') {
    if (Test-Path -LiteralPath $statePath) {
        $entry = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.started) {
            Stop-Process -Id $process.Id
            Write-Output 'Frontend stopped'
        }
        Remove-Item -LiteralPath $statePath
    }
    return
}

if (Test-Path -LiteralPath $statePath) {
    throw 'Run scripts/frontend.ps1 stop before starting another instance.'
}
$frontendDirectory = Join-Path $projectRoot 'frontend'
if (!(Test-Path -LiteralPath (Join-Path $frontendDirectory 'dist/index.html'))) {
    throw 'Build the frontend first: cd frontend; npm ci; npm run build'
}
$node = Get-Command $NodePath -ErrorAction Stop
$port = if ($env:PORT) { [int]$env:PORT } else { 4173 }
$probe = [Net.Sockets.TcpClient]::new()
try {
    $probe.Connect('127.0.0.1', $port)
    throw "Port $port is occupied. Stop the existing frontend first."
} catch [Net.Sockets.SocketException] {
    # Connection refused: the port is free.
} finally { $probe.Dispose() }

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$process = Start-Process -FilePath $node.Source -ArgumentList @('server.mjs') `
    -WorkingDirectory $frontendDirectory -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runDirectory 'frontend.log') `
    -RedirectStandardError (Join-Path $runDirectory 'frontend.err.log')
@{ id = $process.Id; started = $process.StartTime.ToUniversalTime().Ticks.ToString() } |
    ConvertTo-Json | Set-Content -LiteralPath $statePath
Write-Output "Frontend started: http://localhost:$port; logs: target/local/frontend.log"

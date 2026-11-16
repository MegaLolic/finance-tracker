param([ValidateSet('start', 'stop')][string]$Action = 'start')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runDirectory = Join-Path $projectRoot 'target/local'
$statePath = Join-Path $runDirectory 'services.json'

if ($Action -eq 'stop') {
    if (Test-Path -LiteralPath $statePath) {
        foreach ($entry in (Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json)) {
            $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
            # A reused PID must not stop an unrelated process.
            if ($process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.started) {
                Stop-Process -Id $process.Id
                Write-Output "Stopped $($entry.name)"
            }
        }
        Remove-Item -LiteralPath $statePath
    }
    return
}

if (Test-Path -LiteralPath $statePath) {
    throw 'Run scripts/services.ps1 stop before starting another instance.'
}
$modules = @('user-service', 'analytical-service', 'finance-service', 'api-gateway')
$jars = @{}
foreach ($module in $modules) {
    $jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot "$module/target") -Filter '*.jar' |
        Select-Object -First 1
    if (!$jar) { throw "Build $module first: ./user-service/mvnw.cmd -f pom.xml -DskipTests package" }
    $jars[$module] = $jar.FullName
}
foreach ($port in @(8080, 8081, 8082, 9000)) {
    $probe = [Net.Sockets.TcpClient]::new()
    try {
        $probe.Connect('127.0.0.1', $port)
        throw "Port $port is already occupied; stop the existing service first."
    } catch [Net.Sockets.SocketException] {
        # Connection refused means the application's port is available.
    } finally { $probe.Dispose() }
}

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$started = @()
foreach ($module in $modules) {
    $process = Start-Process -FilePath $java -ArgumentList @('-Xmx256m', '-jar', ('"' + $jars[$module] + '"')) `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runDirectory "$module.log") `
        -RedirectStandardError (Join-Path $runDirectory "$module.err.log")
    $started += @{ name = $module; id = $process.Id; started = $process.StartTime.ToUniversalTime().Ticks.ToString() }
    $started | ConvertTo-Json | Set-Content -LiteralPath $statePath
    Write-Output "Started $module (PID $($process.Id)); logs: target/local/$module.log"
}

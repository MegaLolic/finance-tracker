param([ValidateSet('start', 'stop', 'status', 'set-token')][string]$Action = 'status')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$secretFile = Join-Path $projectRoot '.env.ngrok'
$composeFile = Join-Path $projectRoot 'docker-compose.share.yaml'
$containerName = 'finance-assistance-ngrok'

if ($Action -eq 'set-token') {
    $secureToken = Read-Host 'Paste your ngrok authtoken (input is hidden)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    try {
        $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()
        if ($token -notmatch '^[A-Za-z0-9_-]{20,}$') { throw 'Invalid authtoken format. Copy only the token from the ngrok dashboard.' }
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        [IO.File]::WriteAllText($secretFile, "NGROK_AUTHTOKEN=$token`n", $utf8)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $token = $null
        $secureToken.Dispose()
    }
    Write-Output 'Authtoken saved locally in ignored .env.ngrok. Run scripts/share.ps1 start.'
    return
}

if ($Action -eq 'stop') {
    # Only the exact container belonging to this Compose project may be stopped.
    $owner = docker inspect $containerName --format '{{ index .Config.Labels "com.docker.compose.project" }}' 2>$null
    if ($LASTEXITCODE -eq 0) {
        if ($owner -ne 'finance-assistance-ngrok') { throw 'Container belongs to a different project.' }
        docker stop $containerName
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the tunnel.' }
    }
    return
}

if ($Action -eq 'start') {
    if (!(Test-Path -LiteralPath $secretFile)) { throw 'Connect your ngrok account first: scripts/share.ps1 set-token' }
    $response = Invoke-WebRequest -Uri 'http://127.0.0.1:4173/' -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -ne 200) { throw 'Start the frontend on port 4173 first.' }
    docker compose --env-file $secretFile -f $composeFile up -d
    if ($LASTEXITCODE -ne 0) { throw 'Could not start ngrok. Check Docker and your account authtoken.' }
}

$attempts = if ($Action -eq 'start') { 15 } else { 1 }
for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
    $lines = docker logs --tail 100 $containerName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Tunnel is not created yet. Run scripts/share.ps1 set-token, then scripts/share.ps1 start.' }
    $url = $null
    $failureCode = $null
    foreach ($line in $lines) {
        try {
            $entry = $line.ToString() | ConvertFrom-Json
            if ($entry.url -match '^https://[A-Za-z0-9.-]+/?$') { $url = $entry.url }
        } catch { }
        if ($line -match 'ERR_NGROK_\d+') { $failureCode = $matches[0] }
    }
    $running = docker inspect $containerName --format '{{.State.Running}}' 2>$null
    if ($url -and $running -eq 'true') {
        Write-Output "Public URL: $url"
        Write-Output 'The address belongs to your ngrok account. Keep the frontend, backend and Docker running.'
        return
    }
    if ($failureCode) { throw "ngrok rejected the connection ($failureCode). Check the account setup at dashboard.ngrok.com." }
    if ($attempt -lt $attempts - 1) { Start-Sleep -Seconds 2 }
}
Write-Output 'No public URL yet. Run scripts/share.ps1 status again in a few seconds.'

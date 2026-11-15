[CmdletBinding()]
param(
    [ValidateRange(100, 30000)]
    [int]$TimeoutMs = 5000
)

$ErrorActionPreference = 'Stop'
$pbxTarget = '192.168.1.200'
$pbxPort = 8089
$pbxClient = [System.Net.Sockets.TcpClient]::new()
$pbxReachable = $false
$pbxFailure = $null
try {
    $pbxConnect = $pbxClient.ConnectAsync($pbxTarget, $pbxPort)
    if (-not $pbxConnect.Wait($TimeoutMs)) {
        $pbxFailure = 'timeout'
    } else {
        [void]($pbxConnect.GetAwaiter().GetResult())
        $pbxReachable = $pbxClient.Connected
    }
} catch {
    # Retain only a generic socket category, never an exception dump or local paths.
    $pbxError = $_.Exception
    while ($pbxError.InnerException) { $pbxError = $pbxError.InnerException }
    $pbxFailure = if ($pbxError -is [System.Net.Sockets.SocketException]) {
        $pbxError.SocketErrorCode.ToString()
    } else { 'connection_failed' }
} finally {
    $pbxClient.Dispose()
}

$pbxReport = [ordered]@{
    checkedUtc = [DateTime]::UtcNow.ToString('o')
    target = $pbxTarget
    port = $pbxPort
    tcpReachable = $pbxReachable
    failureCategory = $pbxFailure
    timeoutMs = $TimeoutMs
    method = 'TCP handshake only; no application payload, authentication or configuration access'
    limitations = 'Does not verify device identity, administrator access, SIP calling or MTN service'
}
$pbxJson = $pbxReport | ConvertTo-Json
[System.IO.File]::WriteAllText(
    (Join-Path $PSScriptRoot 'connectivity-report.json'), $pbxJson + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false))
$pbxJson
if (-not $pbxReachable) { exit 2 }

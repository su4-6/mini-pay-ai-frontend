param(
    [int]$Port = 4173
)

$ErrorActionPreference = 'Stop'
$python = 'D:\Anaconda3-2024.06-1-Windows-x86_64\Anaconda\python.exe'
$projectRoot = Split-Path -Parent $PSScriptRoot
$documentRoot = Join-Path $projectRoot 'unpackage\dist\build\h5-minipay'
$runtimeDirectory = 'D:\mini-pay-ai-backend\.runtime'

if (-not (Test-Path -LiteralPath $python)) {
    throw "Python runtime not found: $python"
}
if (-not (Test-Path -LiteralPath (Join-Path $documentRoot 'index.html'))) {
    throw "MiniPay H5 build not found: $documentRoot"
}

$bundles = Get-ChildItem -LiteralPath (Join-Path $documentRoot 'assets') -Filter '*.js' -File
$localApi = 'http://127.0.0.1:48081/app-api'
$productionApiHost = 'api.food.minipay.local'
if (Select-String -Path $bundles.FullName -SimpleMatch $productionApiHost -Quiet) {
    throw "Refusing to serve a local H5 bundle containing: $productionApiHost"
}
if (-not (Select-String -Path $bundles.FullName -SimpleMatch $localApi -Quiet)) {
    throw "Refusing to serve a local H5 bundle without: $localApi"
}

$listeners = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
foreach ($listener in $listeners) {
    $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
    if ($null -ne $process -and $process.ProcessName -eq 'python') {
        Stop-Process -Id $process.Id -Force
    }
}

New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
$process = Start-Process -FilePath $python `
    -ArgumentList @('-m', 'http.server', "$Port", '--bind', '127.0.0.1', '--directory', $documentRoot) `
    -WorkingDirectory $documentRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $runtimeDirectory 'h5.out.log') `
    -RedirectStandardError (Join-Path $runtimeDirectory 'h5.err.log') `
    -PassThru
Write-Output "MiniPay H5 started: pid=$($process.Id), url=http://127.0.0.1:$Port"

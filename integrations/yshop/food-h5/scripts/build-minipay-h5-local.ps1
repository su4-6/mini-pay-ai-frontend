param(
    [string]$HBuilderXRoot = $env:HBUILDERX_ROOT,
    [string]$OutputDirectory = "unpackage\dist\build\h5-minipay"
)

$ErrorActionPreference = "Stop"
$buildScript = Join-Path $PSScriptRoot "build-minipay-h5.ps1"

# Android reaches the host through `adb reverse`; using a `.local` DNS name here
# would fail on a physical device unless a separate device-side DNS setup exists.
& $buildScript `
    -HBuilderXRoot $HBuilderXRoot `
    -OutputDirectory $OutputDirectory `
    -H5RouterBase "/" `
    -ApiBaseUrl "http://127.0.0.1:48081/app-api"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
$bundles = Get-ChildItem -LiteralPath (Join-Path $resolvedOutput 'assets') -Filter '*.js' -File
$localApi = 'http://127.0.0.1:48081/app-api'
$productionApiHost = 'api.food.minipay.local'
if (Select-String -Path $bundles.FullName -SimpleMatch $productionApiHost -Quiet) {
    throw "Local MiniPay H5 contains the production API host: $productionApiHost"
}
if (-not (Select-String -Path $bundles.FullName -SimpleMatch $localApi -Quiet)) {
    throw "Local MiniPay H5 does not contain the expected API URL: $localApi"
}

Write-Output "Local MiniPay H5 API verified: $localApi"

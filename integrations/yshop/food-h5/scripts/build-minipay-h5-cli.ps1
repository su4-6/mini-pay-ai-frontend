# Builds the MiniPay food H5 with the standalone uni-app CLI, WITHOUT HBuilderX.
#
# Why this exists
# ---------------
# scripts/build-minipay-h5.ps1 requires the compiler bundled with HBuilderX
# (plugins\uniapp-cli-vite\...\uni.cmd). HBuilderX is a GUI IDE: it is absent on
# CI runners and on dev machines that only have Node, which made the committed
# bundle in unpackage/dist/build/h5-minipay effectively hand-made.
#
# This script reproduces the same artifact from source using only npm packages.
#
# Measured facts (2026-09-11) -- do not "simplify" these away:
#
#  1. `npm install @dcloudio/...` resolves the wrong line by default.
#     The `latest` dist-tag of @dcloudio/vite-plugin-uni points at
#     3.0.0-alpha-3000020210521001 (a 2021 alpha) and @dcloudio/uni-h5@latest is
#     the Vue2 line. Use the pinned stable version below, which is published
#     consistently across every @dcloudio package we need.
#  2. @dcloudio/vite-plugin-uni declares peerDependency vite 5.2.8 and uni-h5
#     requires @vue/server-renderer 3.4.21, so Vue and Vite must be pinned too.
#  3. `pinia: ^2.1.6` resolves to 2.3.1, whose peer range is vue ^3.5.11 and
#     therefore conflicts with Vue 3.4.21. 2.1.7 is the pinned fix.
#  4. A uni-app *CLI* project keeps its sources in src/. This project uses the
#     HBuilderX layout (sources at the project root), so UNI_INPUT_DIR must be
#     pointed at the copied project root or the CLI fails with
#     ENOENT ...\src\manifest.json.
#  5. The build must run from an ASCII-only path in this repository's
#     environment: the checkout path contains CJK characters and the host name is
#     non-ASCII, which corrupts the paths the toolchain passes to child processes.
#
# The build is non-destructive by default: it writes to a sibling directory and
# leaves the committed bundle untouched. Pass -Publish to replace it, after
# reviewing the diff (asset hashes change even when the build is equivalent).
[CmdletBinding()]
param(
    [string]$ApiBaseUrl = "/app-api",
    [string]$RouterBase = "/",
    [string]$OutputDirectory = "unpackage\dist\build\h5-minipay-cli",
    [string]$UniAppVersion = "3.0.0-5020420260813003",
    [string]$VueVersion = "3.4.21",
    [string]$ViteVersion = "5.2.8",
    [switch]$Publish,
    [switch]$KeepWorkspace
)

$ErrorActionPreference = "Stop"

$projectDirectory = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $projectDirectory "manifest.json"))) {
    throw "manifest.json not found; expected the food-h5 project next to this script: $projectDirectory"
}

$resolvedProject = [IO.Path]::GetFullPath($projectDirectory)
$resolvedOutput = [IO.Path]::GetFullPath((Join-Path $projectDirectory $OutputDirectory))
if (-not $resolvedOutput.StartsWith($resolvedProject + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Output directory must stay inside the project: $resolvedOutput"
}

# Stage under an ASCII-only path (see note 5 above).
$workspace = Join-Path ([IO.Path]::GetTempPath()) ("yshop-minipay-h5-cli-" + [Guid]::NewGuid().ToString("N"))
Write-Host "==> Staging project at $workspace" -ForegroundColor Cyan
New-Item -ItemType Directory -Path $workspace -Force | Out-Null

$buildSucceeded = $false
try {
    Get-ChildItem -LiteralPath $projectDirectory -Force |
        Where-Object { $_.Name -notin @(".git", "node_modules", "unpackage") } |
        Copy-Item -Destination $workspace -Recurse -Force

    # Inject the CLI toolchain into the staged manifest only; the committed
    # package.json stays as the HBuilderX project declares it.
    $stagedPackagePath = Join-Path $workspace "package.json"
    $stagedPackage = Get-Content -LiteralPath $stagedPackagePath -Raw | ConvertFrom-Json
    $stagedPackage.scripts | Add-Member -NotePropertyName "build:h5" -NotePropertyValue "uni build -p h5" -Force
    $stagedPackage.dependencies | Add-Member -NotePropertyName "pinia" -NotePropertyValue "2.1.7" -Force
    $devDependencies = [ordered]@{
        "@dcloudio/uni-app"           = $UniAppVersion
        "@dcloudio/uni-cli-shared"    = $UniAppVersion
        "@dcloudio/uni-components"    = $UniAppVersion
        "@dcloudio/uni-h5"            = $UniAppVersion
        "@dcloudio/uni-stacktracey"   = $UniAppVersion
        "@dcloudio/vite-plugin-uni"   = $UniAppVersion
        "@vue/server-renderer"        = $VueVersion
        "vite"                        = $ViteVersion
        "vue"                         = $VueVersion
    }
    $stagedPackage | Add-Member -NotePropertyName devDependencies -NotePropertyValue ([PSCustomObject]$devDependencies) -Force
    [IO.File]::WriteAllText($stagedPackagePath, ($stagedPackage | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
    Remove-Item -LiteralPath (Join-Path $workspace "package-lock.json") -Force -ErrorAction SilentlyContinue

    Write-Host "==> Installing the uni-app CLI toolchain (pinned $UniAppVersion)" -ForegroundColor Cyan
    Push-Location $workspace
    try {
        & npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw "npm install failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }

    # Point the router base at the reverse-proxied root instead of the manifest's /h5/.
    $stagedManifest = Join-Path $workspace "manifest.json"
    $manifestContent = Get-Content -LiteralPath $stagedManifest -Raw
    $updatedManifest = [Text.RegularExpressions.Regex]::Replace(
        $manifestContent, '("base"\s*:\s*)"/h5/"', ('${1}"' + $RouterBase + '"'), 1)
    if ($updatedManifest -eq $manifestContent) {
        throw "MiniPay H5 router base was not found in manifest.json."
    }
    [IO.File]::WriteAllText($stagedManifest, $updatedManifest, [Text.UTF8Encoding]::new($false))

    $stagedOutput = Join-Path $workspace "cli-output"
    $env:NODE_ENV = "production"
    $env:UNI_PLATFORM = "h5"
    $env:UNI_INPUT_DIR = $workspace          # note 4: HBuilderX layout, sources at the root
    $env:UNI_OUTPUT_DIR = $stagedOutput
    $env:VITE_API_URL = $ApiBaseUrl
    $env:VUE_APP_API_URL = $ApiBaseUrl
    $env:UNI_MINIPAY_FOOD = "true"
    $env:VITE_MINIPAY_FOOD = "true"

    Write-Host "==> Building the H5 bundle" -ForegroundColor Cyan
    Push-Location $workspace
    try {
        & npx uni build -p h5 --mode minipay
        if ($LASTEXITCODE -ne 0) { throw "uni build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path -LiteralPath (Join-Path $stagedOutput "index.html"))) {
        throw "Build produced no index.html under $stagedOutput"
    }

    # Same completeness checks as the HBuilderX script.
    $requiredAssets = @(
        "pages-index-index*.js",
        "pages-menu-menu*.js",
        "pages-components-pages-pay-pay*.js"
    )
    foreach ($pattern in $requiredAssets) {
        $matching = Get-ChildItem -Path (Join-Path $stagedOutput "assets") -Filter $pattern -File -ErrorAction SilentlyContinue
        if (-not $matching) { throw "MiniPay H5 build is incomplete; missing asset: $pattern" }
    }

    # The H5 nginx reverse-proxies /app-api/ to yshop-server, so absolute hosts
    # must never be baked in (this was a real outage: a missing :18080 port).
    $absoluteHits = Get-ChildItem -Path $stagedOutput -Recurse -File -Include "*.js", "*.html" |
        Select-String -Pattern "https?://[a-z0-9.-]*minipay\.local" -List
    if ($absoluteHits) {
        throw "Bundle hardcodes a MiniPay host: $($absoluteHits[0].Path)"
    }

    if (Test-Path -LiteralPath $resolvedOutput) { Remove-Item -LiteralPath $resolvedOutput -Recurse -Force }
    New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedOutput) -Force | Out-Null
    Copy-Item -Path $stagedOutput -Destination $resolvedOutput -Recurse -Force

    $fileCount = (Get-ChildItem -LiteralPath $resolvedOutput -Recurse -File).Count
    Write-Host "==> Built $fileCount files into $resolvedOutput" -ForegroundColor Green

    if ($Publish) {
        Write-Warning "Replace the committed bundle and refresh the recorded hash:"
        Write-Warning "  scripts/k3s/verify-h5-bundle.ps1 -Update"
        Write-Warning "Validate the Web Message Bridge (one-time login code -> order -> pay) on a real device before shipping."
    }
    $buildSucceeded = $true
} finally {
    if (-not $KeepWorkspace -and $buildSucceeded -and (Test-Path -LiteralPath $workspace)) {
        Remove-Item -LiteralPath $workspace -Recurse -Force -ErrorAction SilentlyContinue
    } elseif (Test-Path -LiteralPath $workspace) {
        Write-Warning "Build workspace retained for diagnosis: $workspace"
    }
}

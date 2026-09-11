param(
    [string]$HBuilderXRoot = $env:HBUILDERX_ROOT,
    [string]$CompilerPath = $env:UNI_CLI_COMPILER,
    [string]$OutputDirectory = "unpackage\dist\build\h5-minipay",
    [string]$H5RouterBase = "/",
    [string]$ApiBaseUrl = $env:MINIPAY_H5_API_URL
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($CompilerPath) -and
    [string]::IsNullOrWhiteSpace($HBuilderXRoot)) {
    $HBuilderXRoot = Get-ChildItem -Path "D:\" -Directory -Filter "HBuilderX.*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName "HBuilderX" } |
        Where-Object { Test-Path (Join-Path $_ "plugins\uniapp-cli-vite\node_modules\.bin\uni.cmd") } |
        Select-Object -First 1
}

if ([string]::IsNullOrWhiteSpace($CompilerPath)) {
    if ([string]::IsNullOrWhiteSpace($HBuilderXRoot)) {
        throw "UniApp compiler was not found. Set -CompilerPath, UNI_CLI_COMPILER, or HBUILDERX_ROOT."
    }
    $CompilerPath = Join-Path $HBuilderXRoot "plugins\uniapp-cli-vite\node_modules\.bin\uni.cmd"
}
$compiler = [IO.Path]::GetFullPath($CompilerPath)
if (-not (Test-Path $compiler)) {
    throw "UniApp compiler was not found: $compiler"
}
if (-not [string]::IsNullOrWhiteSpace($HBuilderXRoot)) {
    $bundledNodeDirectory = Join-Path $HBuilderXRoot "plugins\node"
    if (Test-Path (Join-Path $bundledNodeDirectory "node.exe")) {
        $env:Path = $bundledNodeDirectory + [IO.Path]::PathSeparator + $env:Path
    }
}

$projectDirectory = Split-Path -Parent $PSScriptRoot
$resolvedOutput = Join-Path $projectDirectory $OutputDirectory
$resolvedProject = [IO.Path]::GetFullPath($projectDirectory)
$resolvedOutput = [IO.Path]::GetFullPath($resolvedOutput)
if (-not $resolvedOutput.StartsWith($resolvedProject + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Output directory must stay inside the project: $resolvedOutput"
}
if (Test-Path -LiteralPath $resolvedOutput) {
    Remove-Item -LiteralPath $resolvedOutput -Recurse -Force
}
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporaryProject = Join-Path $temporaryRoot ("yshop-minipay-h5-" + [Guid]::NewGuid().ToString("N"))
$sourceNodeModules = Join-Path $projectDirectory "node_modules"
$temporaryNodeModules = Join-Path $temporaryProject "node_modules"
$compilerOwnedVuePackages = @(
    "vue",
    "@vue/compiler-core",
    "@vue/compiler-dom",
    "@vue/compiler-sfc",
    "@vue/compiler-ssr",
    "@vue/reactivity",
    "@vue/runtime-core",
    "@vue/runtime-dom",
    "@vue/server-renderer",
    "@vue/shared"
)

New-Item -ItemType Directory -Path $temporaryProject | Out-Null
Get-ChildItem -LiteralPath $projectDirectory -Force |
    Where-Object { $_.Name -notin @(".git", "node_modules", "unpackage") } |
    Copy-Item -Destination $temporaryProject -Recurse -Force
if (Test-Path -LiteralPath $sourceNodeModules) {
    New-Item -ItemType Directory -Path $temporaryNodeModules | Out-Null
    $projectPackage = Get-Content -LiteralPath (Join-Path $projectDirectory "package.json") -Raw |
        ConvertFrom-Json
    $pendingPackages = [Collections.Generic.Queue[string]]::new()
    $copiedPackages = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::OrdinalIgnoreCase
    )
    $projectPackage.dependencies.PSObject.Properties.Name |
        ForEach-Object { $pendingPackages.Enqueue($_) }

    while ($pendingPackages.Count -gt 0) {
        $packageName = $pendingPackages.Dequeue()
        if ([string]::IsNullOrWhiteSpace($packageName)) {
            continue
        }
        if (-not $copiedPackages.Add($packageName)) {
            continue
        }
        # UniApp's compiler owns Vue. Copying the project's newer Vue runtime causes
        # compiler/runtime slot metadata to become incompatible at page mount time.
        if ($compilerOwnedVuePackages -contains $packageName) {
            continue
        }

        $relativePackagePath = $packageName.Replace('/', [IO.Path]::DirectorySeparatorChar)
        $sourcePackage = Join-Path $sourceNodeModules $relativePackagePath
        if (-not (Test-Path -LiteralPath $sourcePackage -PathType Container)) {
            throw "Runtime dependency was not found: $packageName"
        }
        $destinationPackage = Join-Path $temporaryNodeModules $relativePackagePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $destinationPackage) -Force |
            Out-Null
        Copy-Item -LiteralPath $sourcePackage -Destination $destinationPackage -Recurse -Force

        $dependencyManifest = Join-Path $sourcePackage "package.json"
        if (Test-Path -LiteralPath $dependencyManifest) {
            $dependencyPackage = $null
            try {
                $dependencyPackage = Get-Content -LiteralPath $dependencyManifest -Raw |
                    ConvertFrom-Json
            } catch {
                # Some legacy third-party manifests contain damaged non-ASCII metadata.
                # Their runtime bundle is still usable and they have no required children.
                continue
            }
            if ($dependencyPackage.dependencies) {
                $dependencyPackage.dependencies.PSObject.Properties.Name |
                    ForEach-Object { $pendingPackages.Enqueue($_) }
            }
        }
    }
}

# A standalone UniApp CLI resolves the project Vite config from the temporary
# project. Link only compiler-owned framework packages so the build uses the
# compiler's matching Vue/UniApp versions while keeping application packages local.
$compilerNodeModules = Split-Path -Parent (Split-Path -Parent $compiler)
foreach ($packageName in @("vue", "vite", "sass", "less")) {
    $sourcePackage = Join-Path $compilerNodeModules $packageName
    $destinationPackage = Join-Path $temporaryNodeModules $packageName
    if ((Test-Path -LiteralPath $sourcePackage) -and
        -not (Test-Path -LiteralPath $destinationPackage)) {
        New-Item -ItemType Junction -Path $destinationPackage -Target $sourcePackage | Out-Null
    }
}
foreach ($scopeName in @("@dcloudio", "@vue")) {
    $sourceScope = Join-Path $compilerNodeModules $scopeName
    $destinationScope = Join-Path $temporaryNodeModules $scopeName
    if (-not (Test-Path -LiteralPath $sourceScope)) { continue }
    New-Item -ItemType Directory -Path $destinationScope -Force | Out-Null
    Get-ChildItem -LiteralPath $sourceScope -Directory | ForEach-Object {
        $destinationPackage = Join-Path $destinationScope $_.Name
        if (-not (Test-Path -LiteralPath $destinationPackage)) {
            New-Item -ItemType Junction -Path $destinationPackage -Target $_.FullName | Out-Null
        }
    }
}

# UniApp discovers platform compilers from the CLI context package manifest.
# The legacy project manifest only lists application dependencies, so register
# the H5 compiler in the temporary copy without changing the source manifest.
$temporaryPackagePath = Join-Path $temporaryProject "package.json"
$temporaryPackageJson = Get-Content -LiteralPath $temporaryPackagePath -Raw | ConvertFrom-Json
if (-not $temporaryPackageJson.devDependencies) {
    $temporaryPackageJson | Add-Member -NotePropertyName devDependencies -NotePropertyValue ([PSCustomObject]@{})
}
$temporaryPackageJson.devDependencies |
    Add-Member -NotePropertyName "@dcloudio/uni-h5" -NotePropertyValue "*" -Force
[IO.File]::WriteAllText(
    $temporaryPackagePath,
    ($temporaryPackageJson | ConvertTo-Json -Depth 100),
    [Text.UTF8Encoding]::new($false)
)

$temporaryManifest = Join-Path $temporaryProject "manifest.json"
$manifestContent = Get-Content -LiteralPath $temporaryManifest -Raw
$basePattern = '("base"\s*:\s*)"/h5/"'
$baseReplacement = '${1}"' + $H5RouterBase + '"'
$updatedManifest = [Text.RegularExpressions.Regex]::Replace(
    $manifestContent,
    $basePattern,
    $baseReplacement,
    1
)
if ($updatedManifest -eq $manifestContent) {
    throw "MiniPay H5 router base was not found in manifest.json."
}
[IO.File]::WriteAllText($temporaryManifest, $updatedManifest, [Text.UTF8Encoding]::new($false))

if (-not [string]::IsNullOrWhiteSpace($HBuilderXRoot)) {
    $env:HX_APP_ROOT = $HBuilderXRoot
    $env:HX_Version = Split-Path -Leaf (Split-Path -Parent $HBuilderXRoot)
    $env:RUN_BY_HBUILDERX = "true"
}
$env:NODE_ENV = "production"
$env:UNI_PLATFORM = "h5"
$env:UNI_CLI_CONTEXT = $temporaryProject
$env:UNI_INPUT_DIR = $temporaryProject
$env:UNI_OUTPUT_DIR = $resolvedOutput
if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    $env:VUE_APP_API_URL = $ApiBaseUrl.TrimEnd('/')
    $env:VITE_API_URL = $ApiBaseUrl.TrimEnd('/')
}

Push-Location $temporaryProject
$buildSucceeded = $false
try {
    & $compiler build -p h5 -m minipay --minify terser
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    $requiredAssets = @(
        "pages-index-index*.js",
        "pages-menu-menu*.js",
        "pages-components-pages-pay-pay*.js"
    )
    foreach ($pattern in $requiredAssets) {
        $matchingAssets = Get-ChildItem `
            -LiteralPath (Join-Path $resolvedOutput "assets") `
            -Filter $pattern `
            -File `
            -ErrorAction SilentlyContinue
        if (-not $matchingAssets) {
            throw "MiniPay H5 build is incomplete; missing asset: $pattern"
        }
    }
    $buildSucceeded = $true
} finally {
    Pop-Location
    $resolvedTemporaryProject = [IO.Path]::GetFullPath($temporaryProject)
    if ($resolvedTemporaryProject.StartsWith($temporaryRoot, [StringComparison]::OrdinalIgnoreCase) -and
        $resolvedTemporaryProject -ne $temporaryRoot -and
        (Test-Path -LiteralPath $resolvedTemporaryProject) -and $buildSucceeded) {
        Remove-Item -LiteralPath $resolvedTemporaryProject -Recurse -Force
    } elseif (Test-Path -LiteralPath $resolvedTemporaryProject) {
        Write-Warning "Failed build workspace retained for diagnosis: $resolvedTemporaryProject"
    }
}

Write-Output "MiniPay H5 build completed: $resolvedOutput"

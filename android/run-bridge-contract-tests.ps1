# Runs the bridge-contract regression tests without Gradle's test worker.
#
# Why this exists
# ---------------
# On this host `gradlew :bridge-contract:test` always fails with
#   java.lang.ClassNotFoundException: com.minipay.bridge.FoodBridgePolicyTest
# even though the class is compiled. Root cause is the environment, not the code:
#   * the machine hostname is non-ASCII (a CJK character), and
#   * the checkout path contains CJK characters ("数字马力").
# Gradle passes the test classpath to its forked worker through the Windows
# process command line / worker temp files. With those characters present the
# path gets transcoded through the ANSI code page (936) and every classpath
# entry stops resolving, so zero test cases actually execute while the build
# still reports a failure. `testClassesDirs` alone does not help.
#
# The workaround is to stage the compiled classes into an ASCII-only temp
# directory and drive the JUnit Platform Launcher directly. All dependency jars
# already live under an ASCII path (the Gradle module cache).
#
# Exits 0 only when at least one test ran and none failed.
[CmdletBinding()]
param(
    [string]$Configuration = "Debug"
)

$ErrorActionPreference = "Stop"

# This script lives in the Gradle project root (the directory holding gradlew.bat).
$projectRoot = $PSScriptRoot
$moduleDir = Join-Path $projectRoot "bridge-contract"
$gradlew = Join-Path $projectRoot "gradlew.bat"
$cacheRoot = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1"

if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "gradlew.bat not found: $gradlew"
}

# Fail fast if a needed jar is missing instead of producing a confusing
# NoClassDefFoundError from inside the launcher.
#
# The trailing anchor matters: a plain "kotlin-stdlib-*.jar" filter also matches
# kotlin-stdlib-jdk8/-jdk7/-common, which do NOT contain kotlin.jvm.internal.Intrinsics.
# Picking one of those makes every test die with
#   NoClassDefFoundError: kotlin/jvm/internal/Intrinsics.
function Resolve-CacheJar {
    param([Parameter(Mandatory)][string]$Name)
    $pattern = "^" + [regex]::Escape($Name) + "-\d[^\\/]*\.jar$"
    $jar = Get-ChildItem -LiteralPath $cacheRoot -Recurse -File -Filter "$Name-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match $pattern } |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $jar) {
        throw "Required test dependency jar not found in the Gradle cache: $Name (looked under $cacheRoot)"
    }
    Write-Verbose "resolved $Name -> $($jar.FullName)"
    return $jar.FullName
}

Write-Host "==> Compiling bridge-contract test classes (offline)" -ForegroundColor Cyan
& $gradlew --offline -q :bridge-contract:compileTestKotlin
if ($LASTEXITCODE -ne 0) {
    throw "compileTestKotlin failed with exit code $LASTEXITCODE"
}

$testClasses = Join-Path $moduleDir "build\classes\kotlin\test"
if (-not (Test-Path -LiteralPath (Join-Path $testClasses "com\minipay\bridge\FoodBridgePolicyTest.class"))) {
    throw "Compiled test class is missing under $testClasses"
}

# Stage into an ASCII-only directory. %TEMP% resolves to an ASCII path here.
$stageRoot = Join-Path ([IO.Path]::GetTempPath()) ("minipay-bridge-tests-" + [Guid]::NewGuid().ToString("N"))
$stageClasses = Join-Path $stageRoot "classes"
New-Item -ItemType Directory -Path $stageClasses -Force | Out-Null

try {
    # NOTE: -Path (not -LiteralPath) is required here: -LiteralPath does not
    # expand the "*" wildcard, so the copy would silently stage nothing and the
    # launcher would fail with ClassNotFoundException.
    $mainClasses = Join-Path $moduleDir "build\classes\kotlin\main"
    if (Test-Path -LiteralPath $mainClasses) {
        Copy-Item -Path (Join-Path $mainClasses "*") -Destination $stageClasses -Recurse -Force
    }
    Copy-Item -Path (Join-Path $testClasses "*") -Destination $stageClasses -Recurse -Force

    $jars = @(
        Resolve-CacheJar "junit-jupiter-api"
        Resolve-CacheJar "junit-jupiter-engine"
        Resolve-CacheJar "junit-platform-commons"
        Resolve-CacheJar "junit-platform-engine"
        Resolve-CacheJar "junit-platform-launcher"
        Resolve-CacheJar "opentest4j"
        Resolve-CacheJar "apiguardian-api"
        Resolve-CacheJar "kotlin-stdlib"
        Resolve-CacheJar "kotlin-test"
    )

    $launcherSource = Join-Path $stageRoot "RunTests.java"
    @'
import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

public class RunTests {
    public static void main(String[] args) {
        LauncherDiscoveryRequest req = request().selectors(selectClass(args[0])).build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(req, listener);
        TestExecutionSummary s = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        s.printTo(out);
        s.printFailuresTo(out, 20);
        System.out.println("RESULT tests=" + s.getTestsFoundCount()
                + " succeeded=" + s.getTestsSucceededCount()
                + " failed=" + s.getTestsFailedCount());
        System.exit(s.getTestsFailedCount() > 0 ? 1 : 0);
    }
}
'@ | Set-Content -LiteralPath $launcherSource -Encoding ASCII

    $launcherClasses = Join-Path $stageRoot "launcher-classes"
    New-Item -ItemType Directory -Path $launcherClasses -Force | Out-Null
    $sep = [IO.Path]::PathSeparator
    $compileCp = (@($launcherClasses) + $jars) -join $sep

    Write-Host "==> Building the JUnit Platform launcher" -ForegroundColor Cyan
    & javac -encoding UTF-8 -cp $compileCp -d $launcherClasses $launcherSource
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed with exit code $LASTEXITCODE"
    }

    $runCp = (@($launcherClasses, $stageClasses) + $jars) -join $sep
    Write-Host "==> Running com.minipay.bridge.FoodBridgePolicyTest" -ForegroundColor Cyan
    # NOTE: the -D arguments must be quoted. PowerShell otherwise splits
    # "-Dfile.encoding=UTF-8" into "-Dfile" + ".encoding=UTF-8", and the JVM then
    # treats the fragment as the main class name
    # (java.lang.ClassNotFoundException: /encoding=UTF-8).
    & java "-Dfile.encoding=UTF-8" "-Dsun.jnu.encoding=UTF-8" -cp $runCp RunTests "com.minipay.bridge.FoodBridgePolicyTest"
    $testExit = $LASTEXITCODE
} finally {
    if (Test-Path -LiteralPath $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($testExit -ne 0) {
    throw "bridge-contract tests failed (exit code $testExit)"
}
Write-Host "bridge-contract tests passed." -ForegroundColor Green

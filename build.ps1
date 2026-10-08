# Builds the one deployable jar: React app first, then the Spring jar that
# bundles it. Output: deploy/krugworld.jar, next to the Dockerfile that runs it.
#
#   .\build.ps1             build with tests (needs Docker running)
#   .\build.ps1 -SkipTests  build only
param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# The portable JDK from Krugger's Tavern, unless JAVA_HOME already points somewhere.
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = Join-Path $root '..\KruggerTavern\.buildtools\jdk-17.0.20.1+1'
}

Push-Location (Join-Path $root 'frontend')
try {
    # Install only when missing: `npm ci` wipes node_modules first, which
    # Windows refuses while a dev server holds one of its files open.
    if (-not (Test-Path node_modules)) {
        npm ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
} finally { Pop-Location }

Push-Location (Join-Path $root 'backend')
try {
    $mvnArgs = @('-q', 'package')
    if ($SkipTests) { $mvnArgs += '-DskipTests' }
    & .\mvnw.cmd @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }
} finally { Pop-Location }

Copy-Item (Join-Path $root 'backend\target\krugworld-0.1.0.jar') (Join-Path $root 'deploy\krugworld.jar') -Force
Write-Host 'Built deploy\krugworld.jar'

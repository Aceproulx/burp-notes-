param()

$JAVA_HOME_CANDIDATE = "C:\Program Files\Java\jdk-23"
$MVN_LOCAL = Join-Path $PSScriptRoot "maven"
$MVN_VERSION = "3.9.6"
$MVN_BASE_URL = "https://archive.apache.org/dist/maven/maven-3"
$MVN_EXTRACT = Join-Path $MVN_LOCAL "apache-maven-$MVN_VERSION"
$MVN_EXE = Join-Path $MVN_EXTRACT "bin\mvn.cmd"

Write-Host "================================================"
Write-Host "  Burp Suite Notes++ Extension Builder"
Write-Host "================================================"
Write-Host ""

if (-not $env:JAVA_HOME) {
    if (Test-Path $JAVA_HOME_CANDIDATE) {
        $env:JAVA_HOME = $JAVA_HOME_CANDIDATE
        Write-Host "[INFO] JAVA_HOME -> $env:JAVA_HOME"
    }
}

try {
    $v = & java -version 2>&1
    Write-Host "[INFO] $v"
}
catch {
    Write-Host "[ERROR] Java not found."
    exit 1
}

if (-not (Test-Path $MVN_EXE)) {
    Write-Host "[INFO] Downloading Maven $MVN_VERSION..."
    if (-not (Test-Path $MVN_LOCAL)) {
        New-Item -ItemType Directory -Path $MVN_LOCAL | Out-Null
    }
    $zipName = "apache-maven-$MVN_VERSION-bin.zip"
    $zipPath = Join-Path $MVN_LOCAL $zipName
    $dlUrl = "$MVN_BASE_URL/$MVN_VERSION/binaries/$zipName"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $wc = New-Object Net.WebClient
    $wc.DownloadFile($dlUrl, $zipPath)
    Write-Host "[INFO] Extracting..."
    Expand-Archive -Path $zipPath -DestinationPath $MVN_LOCAL -Force
    Remove-Item $zipPath
    Write-Host "[INFO] Maven ready."
}
else {
    Write-Host "[INFO] Using cached Maven at $MVN_EXTRACT"
}

Write-Host ""
Write-Host "[INFO] Running mvn clean package..."
Write-Host ""

$env:PATH = "$MVN_EXTRACT\bin;$env:PATH"
& $MVN_EXE clean package

if ($LASTEXITCODE -eq 0) {
    $jar = Join-Path $PSScriptRoot "target\burp-notes-plus-1.0-all.jar"
    Write-Host ""
    Write-Host "================================================"
    Write-Host "  BUILD SUCCESSFUL!"
    Write-Host "================================================"
    Write-Host ""
    Write-Host "  JAR: $jar"
    Write-Host ""
    Write-Host "  Install in Burp Suite:"
    Write-Host "  1. Open Burp Suite"
    Write-Host "  2. Extensions tab -> Add"
    Write-Host "  3. Extension type: Java"
    Write-Host "  4. Select the JAR above"
    Write-Host "  5. Click Next -- Notes++ tab will appear!"
    Write-Host ""
}
else {
    Write-Host ""
    Write-Host "[ERROR] Build FAILED. See output above."
    exit 1
}

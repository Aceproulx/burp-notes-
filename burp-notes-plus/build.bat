@echo off
echo ================================================
echo  Burp Suite Notes++ Extension Builder
echo ================================================
echo.

where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven (mvn) not found in PATH.
    echo Please install Maven from https://maven.apache.org/download.cgi
    echo and add it to your PATH environment variable.
    pause
    exit /b 1
)

where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java not found in PATH.
    echo Please install JDK 17+ from https://adoptium.net/
    pause
    exit /b 1
)

echo [INFO] Java version:
java -version 2>&1
echo.
echo [INFO] Maven version:
mvn -version 2>&1
echo.
echo [INFO] Building extension...
mvn clean package -q

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================
    echo  BUILD SUCCESSFUL!
    echo ================================================
    echo.
    echo  JAR file: target\burp-notes-plus-1.0-all.jar
    echo.
    echo  To install in Burp Suite:
    echo  1. Open Burp Suite
    echo  2. Go to Extensions tab
    echo  3. Click "Add"
    echo  4. Extension type: Java
    echo  5. Select: %CD%\target\burp-notes-plus-1.0-all.jar
    echo  6. Click "Next" - the Notes++ tab will appear!
    echo.
) else (
    echo.
    echo [ERROR] Build FAILED. Check the output above for errors.
)

pause

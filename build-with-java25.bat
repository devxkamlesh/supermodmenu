@echo off
echo ========================================
echo Building Super Mod Menu for Minecraft 26.2 with Java 25
echo ========================================
echo.

REM Set JAVA_HOME to Java 25 if it is not already configured
REM Uncomment and modify the path below:
REM set JAVA_HOME=C:\Program Files\Java\jdk-25
REM set PATH=%JAVA_HOME%\bin;%PATH%

echo Current Java version:
java -version
echo.

echo Building mod...
gradlew.bat clean build --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build SUCCESS!
    echo ========================================
    echo JAR location: build\libs\supermodmenu-1.3.0.jar
    echo.
    echo This version works with Minecraft 26.2 and Fabric Loader 0.19.3+
) else (
    echo.
    echo ========================================
    echo Build FAILED!
    echo ========================================
    echo.
    echo This Minecraft version requires Java 25.
    echo.
    echo Download Java 25 from:
    echo https://adoptium.net/temurin/releases/?version=25
)

pause

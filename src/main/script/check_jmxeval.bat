@echo off
setlocal enabledelayedexpansion
::
:: JMXEval execution script
::
:: Syntax: check_jmxeval.bat <config-xml-file> [<options>]
::
:: If JAVA_HOME environment variable is not set, uncomment the following
:: line and set the value as the path to root of the Java directory
::
:: set JAVA_HOME="C:\path\to\jdk-1.8.0"
::
:: Execute JMXEval
::
set BASE_DIR=%~dp0
"%JAVA_HOME%\bin\java" -Xmx30m -classpath "%BASE_DIR%\jmxeval\lib\*;%JAVA_HOME%\lib\tools.jar" com.adahas.tools.jmxeval.App %*
set JMX_EXIT_CODE=%ERRORLEVEL%

REM Override exit code for non-production servers
set PROD_STATUS_FILE=%ProgramData%\checkmk\agent\tmp\ProdStatus.txt
if exist "%PROD_STATUS_FILE%" (
    set /p PROD_STATUS=<"%PROD_STATUS_FILE%"
    if "!PROD_STATUS!"=="NONPROD" (
        exit /b 0
    )
)

exit /b %JMX_EXIT_CODE%

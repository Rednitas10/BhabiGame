@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line arguments.
set CMD_LINE_ARGS=%*
if "%CMD_LINE_ARGS%" == "" goto emptyArgs
    set CMD_LINE_ARGS= %CMD_LINE_ARGS%
:emptyArgs

@rem Get the script directory
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Add GRADLE_OPTS to CMD_LINE_ARGS
set CMD_LINE_ARGS=%GRADLE_OPTS% %CMD_LINE_ARGS%

@rem Split up SCRIPT_CMD into SCRIPT_CMD_PROGRAM and SCRIPT_CMD_ARGS
set SCRIPT_CMD_PROGRAM="%JAVA_EXE%"
set SCRIPT_CMD_ARGS=

@rem Construct the classpath for the Gradle Wrapper
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
%SCRIPT_CMD_PROGRAM% %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if not "" == "%GRADLE_EXIT_CONSOLE%" (
  exit 1
) else (
  exit /b 1
)

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME goto javaHome
set "JAVA_EXE=java.exe"
goto run
:javaHome
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
:run
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
set "GRADLE_EXIT=%ERRORLEVEL%"
endlocal & exit /b %GRADLE_EXIT%

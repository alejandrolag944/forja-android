@echo off
setlocal
set APP_HOME=%~dp0
if not defined JAVA_HOME (
  echo JAVA_HOME is not set. Install JDK 17 or open this project in Android Studio.
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

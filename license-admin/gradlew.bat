@echo off
set PROJECT_DIR=%~dp0
call "%PROJECT_DIR%..\gradlew.bat" -p "%PROJECT_DIR%" %*

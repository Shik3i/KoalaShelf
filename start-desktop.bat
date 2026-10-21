@echo off
cd /d "%~dp0apps\desktop"
call "node_modules\.bin\electron.cmd" . %*

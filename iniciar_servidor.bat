@echo off
title BuildAid Sync Server
cd /d "%~dp0server"
echo ==============================================
echo   Iniciando BuildAid Music Sync Server...
echo ==============================================
if not exist node_modules (
    echo Instalando dependencias necessarias...
    call npm install
)
echo Servidor rodando em ws://localhost:3000
echo Pressione Ctrl+C para encerrar o servidor.
node index.js
pause

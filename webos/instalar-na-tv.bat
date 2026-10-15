@echo off
chcp 65001 >nul
title Instalar Player na TV LG
echo.
echo  === Instalar o Player na TV LG (modo desenvolvedor) ===
echo.
echo  Antes: na TV, abra o app Developer Mode, deixe "Dev Mode Status" e "Key Server" LIGADOS.
echo  O computador precisa estar na mesma rede (Wi-Fi/cabo) da TV.
echo  Coloque este arquivo na mesma pasta do arquivo .ipk.
echo.

where node >nul 2>nul
if errorlevel 1 (
  echo  [ERRO] Node.js nao esta instalado. Baixe e instale de https://nodejs.org ^(versao LTS^) e rode este arquivo de novo.
  pause
  exit /b 1
)

where ares-install >nul 2>nul
if errorlevel 1 (
  echo  Instalando as ferramentas da LG ^(so na primeira vez^)...
  call npm install -g @webos-tools/cli
)

set /p TVIP= Digite o IP da TV (aparece no app Developer Mode, ex. 192.168.0.25):
echo.
echo  Cadastrando a TV...
call ares-setup-device --remove tv >nul 2>nul
call ares-setup-device --add tv --info "{'host':'%TVIP%','port':'9922','username':'prisoner'}"
echo.
echo  Agora digite a PASSPHRASE que aparece no app Developer Mode da TV:
call ares-novacom --device tv --getkey
echo.
for %%f in (*.ipk) do (
  echo  Instalando %%f ...
  call ares-install --device tv "%%f"
)
echo.
echo  Abrindo o app na TV...
call ares-launch --device tv com.example.player
echo.
echo  Pronto! Se deu erro, confira o IP, a passphrase e se o Key Server esta ligado.
pause

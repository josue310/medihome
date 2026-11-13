@echo off
REM ==================================================================
REM  Mise a niveau de la base hospital_2 (a lancer UNE fois)
REM  Prerequis : MySQL demarre dans Laragon.
REM  1. sauvegarde la base (avant modification)
REM  2. compile l'outil com.util.SecuritySetup avec le JDK 7
REM  3. cree la table admin, hache les mots de passe en clair (scrypt)
REM     et cree le compte administrateur (mot de passe demande au clavier)
REM  4. cree les tables du module de tele-consultation
REM     (database\teleconsultation.sql, rejouable sans danger)
REM  Usage : setup_securite.bat [email_admin]   (defaut : admin@gmail.com)
REM ==================================================================
setlocal
cd /d "%~dp0"
set "JDK=C:\Java\jdk1.7.0_79\bin"
set "MYSQL=C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin"
set "OUT=%TEMP%\gestionhopital_setup"
set "BACKUP=%USERPROFILE%\hospital_2_backup_avant_securite.sql"

echo [1/4] Sauvegarde de la base hospital_2 vers "%BACKUP%" ...
"%MYSQL%\mysqldump.exe" -uroot hospital_2 > "%BACKUP%"
if errorlevel 1 (
  echo ECHEC de la sauvegarde : MySQL est-il demarre dans Laragon ?
  pause
  exit /b 1
)

echo [2/4] Compilation de l'outil ...
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
"%JDK%\javac" -nowarn -encoding UTF-8 -cp "WebContent\WEB-INF\lib\*" -d "%OUT%" src\com\db\DBConnect.java src\com\util\PasswordUtil.java src\com\util\SecuritySetup.java
if errorlevel 1 (
  pause
  exit /b 1
)

echo [3/4] Migration des mots de passe et compte administrateur ...
"%JDK%\java" -cp "%OUT%;WebContent\WEB-INF\lib\*" com.util.SecuritySetup %1
rmdir /s /q "%OUT%"

echo [4/4] Tables du module de tele-consultation ...
"%MYSQL%\mysql.exe" -uroot hospital_2 < "database\teleconsultation.sql"
if errorlevel 1 echo ECHEC de la creation des tables de tele-consultation

echo.
echo ATTENTION : la sauvegarde "%BACKUP%" contient les anciens mots de passe EN CLAIR.
echo Supprimez-la une fois l'application verifiee.
pause

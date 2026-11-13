@echo off
REM ==================================================================
REM  Exporte les DONNEES de la base locale hospital_2 (sans la structure)
REM  pour les importer dans la base MySQL en ligne (voir DEPLOIEMENT.md).
REM  La table admin est exclue : l'administrateur en ligne est cree par
REM  les variables ADMIN_EMAIL / ADMIN_PASSWORD.
REM ==================================================================
setlocal
set "MYSQL=C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin"
set "OUT=%USERPROFILE%\medihome_donnees.sql"
"%MYSQL%\mysqldump.exe" -uroot --no-create-info --complete-insert --skip-triggers --default-character-set=utf8mb4 --ignore-table=hospital_2.admin hospital_2 > "%OUT%"
if errorlevel 1 (
  echo ECHEC : MySQL est-il demarre dans Laragon ?
  pause
  exit /b 1
)
echo Donnees exportees dans "%OUT%"
echo Ce fichier contient des donnees personnelles : supprimez-le apres l'import.
pause

@echo off
echo ========================================
echo Eventify - Cloud SQL Setup Script
echo ========================================
echo.
echo Make sure you have run setup-gcp.bat first!
echo.

set /p DB_PASSWORD="Enter database password: "

echo.
echo [1/3] Creating Cloud SQL instance (this takes 2-3 minutes)...
gcloud sql instances create eventify-db --database-version=MYSQL_8_0 --tier=db-f1-micro --region=us-central1 --root-password=%DB_PASSWORD%
echo.

echo [2/3] Creating eventify database...
gcloud sql databases create eventify --instance=eventify-db
echo.

echo [3/3] Getting connection name...
for /f "tokens=*" %%i in ('gcloud sql instances describe eventify-db --format="value(connectionName)"') do set CONNECTION_NAME=%%i
echo.
echo Connection Name: %CONNECTION_NAME%
echo.
echo Save this! You'll need it for deployment.
echo.

echo ========================================
echo Cloud SQL Setup Complete!
echo ========================================
echo.
pause

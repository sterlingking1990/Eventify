@echo off
echo ========================================
echo Eventify - GCP Setup Script
echo ========================================
echo.

echo [1/8] Checking gcloud CLI...
gcloud --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: gcloud CLI not installed!
    echo Download from: https://cloud.google.com/sdk/docs/install
    echo After install, run this script again.
    pause
    exit /b 1
)
echo gcloud CLI found!
echo.

echo [2/8] Authenticating with Google Cloud...
gcloud auth login
echo.

echo [3/8] Creating GCP project...
gcloud projects create eventify-ticketing --name="Eventify"
echo.

echo [4/8] Setting active project...
gcloud config set project eventify-ticketing
echo.

echo [5/8] Enabling required APIs...
gcloud services enable run.googleapis.com
gcloud services enable sqladmin.googleapis.com
gcloud services enable containerregistry.googleapis.com
gcloud services enable cloudbuild.googleapis.com
echo.

echo [6/8] Creating service account...
gcloud iam service-accounts create eventify-deployer --display-name="Eventify Deployer"
echo.

echo [7/8] Granting permissions...
gcloud projects add-iam-binding-policy eventify-ticketing --member="serviceAccount:eventify-deployer@eventify-ticketing.iam.gserviceaccount.com" --roles="roles/run.admin"
gcloud projects add-iam-binding-policy eventify-ticketing --member="serviceAccount:eventify-deployer@eventify-ticketing.iam.gserviceaccount.com" --roles="roles/storage.admin"
gcloud projects add-iam-binding-policy eventify-ticketing --member="serviceAccount:eventify-deployer@eventify-ticketing.iam.gserviceaccount.com" --roles="roles/iam.serviceAccountUser"
echo.

echo [8/8] Generating service account key...
gcloud iam service-accounts keys create key.json --iam-account=eventify-deployer@eventify-ticketing.iam.gserviceaccount.com
echo.

echo ========================================
echo GCP Setup Complete!
echo ========================================
echo.
echo Next steps:
echo 1. Enable billing at: https://console.cloud.google.com/billing
echo 2. Create Cloud SQL instance (see instructions)
echo 3. Upload key.json to Jenkins
echo.
pause

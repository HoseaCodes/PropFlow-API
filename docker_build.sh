#!/bin/bash

# Variables
USERNAME="hoseacodes"
APP_NAME="propflow"
VERSION="1.0.0"
DATE=$(date +%Y%m%d)

# Function to check for errors
check_error() {
    if [ $? -ne 0 ]; then
        echo "Error: $1"
        exit 1
    fi
}

echo "📦 Starting build process..."

# Remove old images
echo "🧹 Cleaning up old images..."
docker rmi $USERNAME/$APP_NAME:latest 2>/dev/null || true
docker rmi $USERNAME/$APP_NAME:$VERSION 2>/dev/null || true
docker rmi $USERNAME/$APP_NAME:$DATE 2>/dev/null || true
docker rmi $USERNAME/$APP_NAME:prod 2>/dev/null || true

# Setup buildx
echo "🔧 Setting up Docker buildx..."
docker buildx rm multiarch 2>/dev/null || true
docker buildx create --name multiarch --use
check_error "Failed to create builder"

docker buildx inspect --bootstrap
check_error "Failed to bootstrap builder"

# Build and push for amd64 with multiple tags
echo "🏗️ Building and pushing images..."
docker buildx build \
    --platform=linux/amd64 \
    --push \
    --tag $USERNAME/$APP_NAME:latest \
    --tag $USERNAME/$APP_NAME:$VERSION \
    --tag $USERNAME/$APP_NAME:$DATE \
    --tag $USERNAME/$APP_NAME:prod \
    .
check_error "Failed to build and push images"

echo "✅ Verifying image platforms..."
docker manifest inspect $USERNAME/$APP_NAME:latest
check_error "Failed to verify image"

echo "🎉 Build completed successfully!"
echo "
Images pushed:
📋 $USERNAME/$APP_NAME:latest
📋 $USERNAME/$APP_NAME:$VERSION
📋 $USERNAME/$APP_NAME:$DATE
📋 $USERNAME/$APP_NAME:prod
"

# Optional: Clean up
echo "🧹 Cleaning up build environment..."
docker buildx rm multiarch 2>/dev/null || true
docker system prune -f

echo "✨ All done! Images are ready for deployment."
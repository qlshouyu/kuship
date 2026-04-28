#!/bin/bash
./standalone/images-package.sh
docker buildx build -f standalone/Dockerfile -t rainbond-dev:v6.7.1-release  .
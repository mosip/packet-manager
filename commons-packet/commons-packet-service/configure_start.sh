#!/bin/bash

set -e

echo "Starting prerequisite setup..."

echo "Downloading mandatory dependencies..."

wget -q --show-progress "${kernel_ref_idobjectvalidator_url}" \-O "${loader_path_env}/kernel-ref-idobjectvalidator.jar"
wget -q --show-progress "${iam_adapter_url_env}" \-O "${loader_path_env}/kernel-auth-adapter.jar"

if [ -n "$cache_provider_url_env" ]; then

echo "Cache provider detected. Downloading..."

wget -q --show-progress "${cache_provider_url_env}" \-O "${loader_path_env}/cache-provider.jar"

else

echo "No cache provider configured. Skipping..."

fi

echo "All dependencies downloaded."

exec "$@"
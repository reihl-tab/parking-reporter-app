#!/bin/sh
set -eu
mkdir -p gradle/wrapper
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  curl --fail --location --proto '=https' --tlsv1.2 https://raw.githubusercontent.com/gradle/gradle/v9.8.2/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
fi
printf '%s  %s\n' 3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98 gradle/wrapper/gradle-wrapper.jar | sha256sum -c -

sh scripts/prepare_ocr_model.sh

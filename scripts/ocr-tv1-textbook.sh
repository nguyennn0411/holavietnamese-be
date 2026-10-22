#!/bin/sh
set -eu
mkdir -p /work/ocr
find /pages -name 'page-*.jpg' -print | sort | xargs -P 3 -I '{}' sh -c '
  name=$(basename "$1" .jpg)
  tesseract "$1" "/work/ocr/$name" -l vie+eng --psm 11 txt tsv 2>>/work/ocr.log
' sh '{}'
touch /work/ocr-done

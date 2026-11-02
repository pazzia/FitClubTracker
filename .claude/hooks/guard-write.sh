#!/usr/bin/env bash
# PreToolUse(Write|Edit): protect signing files and generated assets.
source "$(dirname "$0")/lib.sh"
read_input
path="$(edited_path)"

case "$path" in
  docs/debug-signing-sha256.txt)
    block "Отпечаток debug-подписи закреплён намеренно: CI сверяет с ним ключ и APK. Подмена отпечатка сломает обновление установленного приложения (docs/SIGNING.md)." ;;
  *.jks|*.keystore)
    block "Файлы ключей не редактируются и не коммитятся (docs/SIGNING.md)." ;;
esac
exit 0

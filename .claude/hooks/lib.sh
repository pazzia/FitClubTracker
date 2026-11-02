#!/usr/bin/env bash
# Shared helpers for the project's Claude Code hooks.
# The hook input (JSON) arrives on stdin; read it once with read_input.
# Python 3 is used to parse JSON because jq is optional on dev machines.

cd "${CLAUDE_PROJECT_DIR:-$(dirname "${BASH_SOURCE[0]}")/../..}" || exit 0

HOOK_INPUT=""
read_input() { HOOK_INPUT="$(cat)"; }

# json_field <dotted.path>  ->  prints the value ("" when absent)
json_field() {
  HOOK_INPUT="$HOOK_INPUT" python3 - "$1" <<'PY' 2>/dev/null
import json, os, sys
try:
    data = json.loads(os.environ["HOOK_INPUT"])
except Exception:
    sys.exit(0)
for key in sys.argv[1].split("."):
    data = data.get(key) if isinstance(data, dict) else None
    if data is None:
        sys.exit(0)
print(data if isinstance(data, str) else json.dumps(data))
PY
}

# Path of the edited file, relative to the project root.
edited_path() {
  local p
  p="$(json_field tool_input.file_path)"
  printf '%s' "${p#"$PWD"/}"
}

# Text the Write/Edit call is adding (content or new_string).
added_text() {
  local c
  c="$(json_field tool_input.content)"
  [ -n "$c" ] || c="$(json_field tool_input.new_string)"
  printf '%s' "$c"
}

# block <reason>: stop the tool call; Claude sees the reason.
block() { printf '%s\n' "$1" >&2; exit 2; }

# note <event> <text>: let the call through, add context for Claude.
note() {
  EVENT="$1" TEXT="$2" python3 - <<'PY'
import json, os
print(json.dumps({"hookSpecificOutput": {
    "hookEventName": os.environ["EVENT"],
    "additionalContext": os.environ["TEXT"]}}, ensure_ascii=False))
PY
  exit 0
}

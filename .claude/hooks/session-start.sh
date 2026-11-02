#!/usr/bin/env bash
# SessionStart (startup, resume, clear, compact): print where the work stands.
# Stdout becomes context for Claude. Observations only, no verdicts.
source "$(dirname "$0")/lib.sh"

branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)" || exit 0
echo "## Состояние репозитория"
echo "- Ветка: $branch"
if git rev-parse --verify -q "origin/$branch" >/dev/null; then
  echo "- Относительно origin/$branch: $(git rev-list --left-right --count "origin/$branch...HEAD" | awk '{print "позади " $1 ", впереди " $2}')"
fi
dirty="$(git status --porcelain | head -15)"
[ -n "$dirty" ] && { echo "- Незакоммиченные изменения:"; sed 's/^/    /' <<<"$dirty"; }
echo "- Последние коммиты:"
git log --oneline -5 | sed 's/^/    /'

state=".claude/state/active.md"
if [ -s "$state" ]; then
  echo
  echo "## Чекпоинт сессии ($state)"
  tail -40 "$state"
fi
exit 0

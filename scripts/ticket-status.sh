#!/usr/bin/env bash
# Déplace un ticket (issue GitHub) dans une colonne du board GitHub Projects (v2).
#
# Usage : scripts/ticket-status.sh <numéro-issue> "<Statut>"
#   ex. : scripts/ticket-status.sh 42 "In Progress"
#         scripts/ticket-status.sh 42 "Done"
#
# Configuration : .claude/workflow.env (PROJECT_OWNER, PROJECT_NUMBER, REPO, STATUS_FIELD)
# Prérequis     : gh authentifié avec le scope "project" (gh auth refresh -s project), jq.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$ROOT/.claude/workflow.env" ] && source "$ROOT/.claude/workflow.env"

ISSUE="${1:?Usage: $0 <issue-number> <status>}"
STATUS="${2:?Usage: $0 <issue-number> <status>}"
: "${PROJECT_OWNER:?PROJECT_OWNER manquant (.claude/workflow.env)}"
: "${PROJECT_NUMBER:?PROJECT_NUMBER manquant (.claude/workflow.env)}"
REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
STATUS_FIELD="${STATUS_FIELD:-Status}"

PROJECT_ID=$(gh project view "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" --format json --jq .id)

FIELD_JSON=$(gh project field-list "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" --format json \
  | jq --arg f "$STATUS_FIELD" '.fields[] | select(.name == $f)')
FIELD_ID=$(jq -r .id <<<"$FIELD_JSON")
OPTION_ID=$(jq -r --arg s "$STATUS" '.options[] | select(.name == $s) | .id' <<<"$FIELD_JSON")

if [ -z "$OPTION_ID" ]; then
  echo "Statut \"$STATUS\" introuvable. Disponibles : $(jq -r '[.options[].name] | join(", ")' <<<"$FIELD_JSON")" >&2
  exit 1
fi

ITEM_ID=$(gh project item-list "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" --format json --limit 1000 \
  | jq -r --argjson n "$ISSUE" --arg r "$REPO" \
      '.items[] | select(.content.number == $n and .content.repository == $r) | .id' | head -n1)

# L'issue n'est pas encore sur le board : on l'ajoute.
if [ -z "$ITEM_ID" ]; then
  ITEM_ID=$(gh project item-add "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" \
    --url "https://github.com/$REPO/issues/$ISSUE" --format json --jq .id)
fi

gh project item-edit --id "$ITEM_ID" --project-id "$PROJECT_ID" \
  --field-id "$FIELD_ID" --single-select-option-id "$OPTION_ID" >/dev/null

echo "Ticket #$ISSUE → $STATUS"

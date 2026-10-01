#!/usr/bin/env bash
# Déplace un ticket (issue GitHub) dans une colonne du board GitHub Projects (v2).
#
# Usage : scripts/ticket-status.sh <numéro-issue> "<Statut>"
#   ex. : scripts/ticket-status.sh 42 "In Progress"
#         scripts/ticket-status.sh 42 "Done"
#
# Configuration : .claude/workflow.env (PROJECT_OWNER, PROJECT_NUMBER, REPO, STATUS_FIELD)
# Prérequis     : gh authentifié avec les scopes "repo" et "project" (API GraphQL directe).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$ROOT/.claude/workflow.env" ] && source "$ROOT/.claude/workflow.env"

ISSUE="${1:?Usage: $0 <issue-number> <status>}"
STATUS="${2:?Usage: $0 <issue-number> <status>}"
: "${PROJECT_OWNER:?PROJECT_OWNER manquant (.claude/workflow.env)}"
: "${PROJECT_NUMBER:?PROJECT_NUMBER manquant (.claude/workflow.env)}"
REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
STATUS_FIELD="${STATUS_FIELD:-Status}"

DATA=$(gh api graphql \
  -f owner="$PROJECT_OWNER" -F number="$PROJECT_NUMBER" -f field="$STATUS_FIELD" \
  -f repoOwner="${REPO%%/*}" -f repoName="${REPO##*/}" -F issue="$ISSUE" \
  -f query='
query($owner: String!, $number: Int!, $field: String!, $repoOwner: String!, $repoName: String!, $issue: Int!) {
  repositoryOwner(login: $owner) {
    ... on User { projectV2(number: $number) { ...P } }
    ... on Organization { projectV2(number: $number) { ...P } }
  }
  repository(owner: $repoOwner, name: $repoName) {
    issue(number: $issue) { id projectItems(first: 50) { nodes { id project { id } } } }
  }
}
fragment P on ProjectV2 {
  id
  field(name: $field) { ... on ProjectV2SingleSelectField { id options { id name } } }
}')

PROJECT_ID=$(jq -r '.data.repositoryOwner.projectV2.id' <<<"$DATA")
FIELD_ID=$(jq -r '.data.repositoryOwner.projectV2.field.id' <<<"$DATA")
OPTION_ID=$(jq -r --arg s "$STATUS" '.data.repositoryOwner.projectV2.field.options[] | select(.name == $s) | .id' <<<"$DATA")
ISSUE_ID=$(jq -r '.data.repository.issue.id' <<<"$DATA")

if [ -z "$OPTION_ID" ]; then
  echo "Statut \"$STATUS\" introuvable. Disponibles : $(jq -r '[.data.repositoryOwner.projectV2.field.options[].name] | join(", ")' <<<"$DATA")" >&2
  exit 1
fi

ITEM_ID=$(jq -r --arg p "$PROJECT_ID" '.data.repository.issue.projectItems.nodes[] | select(.project.id == $p) | .id' <<<"$DATA" | head -n1)

# L'issue n'est pas encore sur le board : on l'ajoute.
if [ -z "$ITEM_ID" ]; then
  ITEM_ID=$(gh api graphql -f project="$PROJECT_ID" -f content="$ISSUE_ID" -f query='
mutation($project: ID!, $content: ID!) {
  addProjectV2ItemById(input: {projectId: $project, contentId: $content}) { item { id } }
}' --jq '.data.addProjectV2ItemById.item.id')
fi

gh api graphql -f project="$PROJECT_ID" -f item="$ITEM_ID" -f field="$FIELD_ID" -f option="$OPTION_ID" -f query='
mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
  updateProjectV2ItemFieldValue(input: {projectId: $project, itemId: $item, fieldId: $field,
    value: {singleSelectOptionId: $option}}) { projectV2Item { id } }
}' >/dev/null

echo "Ticket #$ISSUE → $STATUS"

#!/bin/bash
input=$(cat)
cmd=$(echo "$input" | jq -r '.tool_input.command // empty')

case "$cmd" in
  ./gradlew*|gradlew*) ;;
  *) exit 0 ;;
esac
case "$cmd" in
  *'|'*|*'>'*|*';'*|*'&&'*) exit 0 ;;
esac

filter='FAILED|error:|^e: |^w: .*deprecat|BUILD (SUCCESSFUL|FAILED)|tests? completed|What went wrong|Caused by|Exception'
new="out=\$($cmd --console=plain 2>&1); rc=\$?; echo \"\$out\" | grep -E '$filter' | head -200; exit \$rc"

echo "$input" | jq --arg c "$new" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "allow",
    permissionDecisionReason: "Gradle output filtered to failures",
    updatedInput: (.tool_input + {command: $c})
  }
}'

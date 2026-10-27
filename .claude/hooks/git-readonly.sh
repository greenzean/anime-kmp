#!/bin/bash
input=$(cat)
cmd=$(echo "$input" | jq -r '.tool_input.command // empty')

case "$cmd" in
  *';'*|*'&'*|*'|'*|*'`'*|*'<'*|*'>'*|*'$('*|*$'\n'*)
    echo "Only a single read-only git command is allowed." >&2
    exit 2
    ;;
esac

case "$cmd" in
  *[[:space:]]--output*|*[[:space:]]-O*|*--open-files-in-pager*|*--ext-diff*|*--textconv*)
    echo "Options that write files or run programs are not allowed." >&2
    exit 2
    ;;
esac

case "$cmd" in
  "git log"|"git log "*|"git grep "*|"git show"|"git show "*|"git ls-files"|"git ls-files "*|"git diff"|"git diff "*|"git status"|"git status "*|"gh issue view "*) exit 0 ;;
  *)
    echo "Only read-only commands are allowed: git log, grep, show, ls-files, diff, status, and gh issue view." >&2
    exit 2
    ;;
esac

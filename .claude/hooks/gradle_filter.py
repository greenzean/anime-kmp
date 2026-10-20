#!/usr/bin/env python3
"""Keep Gradle output out of the context: only failures and the build result get through.

Two modes:
  hook (no arguments): PreToolUse hook for the Bash and PowerShell tools. Reads the hook JSON
      on stdin and rewrites a plain `./gradlew ...` / `.\\gradlew ...` command into
      `<python> gradle_filter.py run ...`. Only plain task names and flags are rewritten;
      anything that pipes, redirects, chains or quotes is left alone.
  run <gradle args>: runs gradlew from the current directory and prints only the matching lines.

Standard library only, so it works on Windows, macOS and Linux without jq.
"""

import json
import os
import re
import subprocess
import sys
from pathlib import Path

# Whitelist, not blacklist: the hook auto-approves what it rewrites, so it only accepts
# `gradlew` followed by plain task names and flags. Anything else (quotes, &, newlines,
# pipes, redirects) is left to the normal permission flow untouched.
GRADLEW = re.compile(r"(?:\./|\.\\)?gradlew(?:\.bat)?(?P<args>(?: [\w:.,=/@+-]+)*)")
KEEP = re.compile(
    r"FAILED|error:|^e: |^w: .*deprecat|BUILD (SUCCESSFUL|FAILED)|tests? completed"
    r"|What went wrong|Caused by|Exception"
)
MAX_LINES = 200
TAIL_ON_UNKNOWN_FAILURE = 30


def hook() -> int:
    try:
        data = json.load(sys.stdin)
    except ValueError:
        return 0  # Not our input: leave the command untouched.
    tool_input = data.get("tool_input") or {}
    command = tool_input.get("command") or ""
    match = GRADLEW.fullmatch(command.strip(" "))
    if not match:
        return 0

    args = match.group("args")
    python = Path(sys.executable)
    script = Path(__file__).resolve()
    if data.get("tool_name") == "PowerShell":
        new_command = f'& "{python}" "{script}" run{args}'
    else:
        new_command = f'"{python.as_posix()}" "{script.as_posix()}" run{args}'

    json.dump(
        {
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "allow",
                "permissionDecisionReason": "Gradle output filtered to failures",
                "updatedInput": {**tool_input, "command": new_command},
            }
        },
        sys.stdout,
    )
    return 0


def run(args: list[str]) -> int:
    gradlew = str(Path.cwd() / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    result = subprocess.run(
        [gradlew, *args, "--console=plain"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    lines = result.stdout.splitlines()
    kept = [line for line in lines if KEEP.search(line)][:MAX_LINES]
    if result.returncode != 0 and not any("What went wrong" in line for line in kept):
        # Unrecognised failure: show the tail instead of hiding it.
        kept += lines[-TAIL_ON_UNKNOWN_FAILURE:]
    print("\n".join(kept))
    return result.returncode


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "run":
        sys.exit(run(sys.argv[2:]))
    sys.exit(hook())

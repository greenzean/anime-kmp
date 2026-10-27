#!/bin/bash
input=$(cat)
file=$(echo "$input" | jq -r '.tool_input.file_path // empty')
session=$(echo "$input" | jq -r '.session_id // "none"')

case "$file" in
  */shared/src/*.kt|*/composeApp/src/*.kt|*/shared/src/*.sq) ;;
  *) exit 0 ;;
esac

base=$(basename "$file")
skill=""
rule=""
case "$base" in
  *ViewModel.kt|*State.kt)
    skill="kmp-presentation-mvi"
    rule="View models take use cases (shared/<feature>/domain), never repositories or data sources. State, Action and Event per screen; one-time effects go through Events; state changes use .update {}."
    ;;
  *UseCase.kt)
    skill="kmp-presentation-mvi"
    rule="One use case per operation in shared/<feature>/domain with a single suspend operator fun invoke; register it with factoryOf in the feature's presentation module. View models inject use cases only."
    ;;
  *Mapper.kt)
    skill="kmp-presentation-mvi"
    rule="UI models end in Ui and hold the formatted values; mapping and formatting live in shared, not in composables."
    ;;
  *Screen.kt|*Root.kt|*Previews.kt)
    skill="kmp-compose-ui"
    rule="Root holds the view model and observes events; Screen takes only state and onAction. Previews in light and dark through AppTheme, 48dp touch targets, contentDescription on controls."
    ;;
  *Route.kt|App.kt)
    skill="kmp-navigation"
    rule="Type-safe @Serializable routes, one graph per feature, cross-feature navigation through lambda callbacks."
    ;;
  *Module.kt)
    skill="kmp-di-koin"
    rule="Prefer viewModelOf, singleOf and factoryOf; use cases are factories registered in the feature's presentation module."
    ;;
  *Repository.kt|*DataSource.kt|*Api.kt|*.sq)
    skill="kmp-data-layer"
    rule="Local database is the source of truth; map DTOs to domain models; errors as typed Results."
    ;;
  *Test.kt)
    skill="kmp-testing"
    rule="Fakes over mocks; view model tests per state and action; runComposeUiTest tests for every screen's states, actions and accessibility labels."
    ;;
  *) exit 0 ;;
esac

marker="${TMPDIR:-/tmp}/claude-skill-reminder-$session-$skill"
[ -e "$marker" ] && exit 0
touch "$marker"

jq -n --arg s "$skill" --arg r "$rule" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    additionalContext: ("Before writing this file, load the " + $s + " skill if it is not loaded in this session. Key rule: " + $r)
  }
}'

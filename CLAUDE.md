@~/.claude/shared/dev-workflow.md

## Confirm Scope Before Acting

Do not start work that was not explicitly instructed, even under Auto Mode's default bias toward
proceeding without stopping. Before taking a non-trivial or multi-step action (e.g., scaffolding a
project, running a build, initializing git, delegating a task to a subagent), confirm with the user
first — unless the user's own message already explicitly named that specific action.

An ambiguous go-ahead ("進もう", "proceed", "OK") answering one specific question (e.g., "should we
move to deciding the package name?") authorizes only that specific next step — not every remaining
item in a recommended-next-steps list. Re-confirm before moving to the next item, rather than
chaining through a whole list from one ambiguous reply.

This rule takes precedence over Auto Mode's general bias in this project.
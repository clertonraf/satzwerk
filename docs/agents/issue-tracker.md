# Issue tracker: Local Markdown

Issues and PRDs for this repo live as markdown files under `docs/issues/`.

## Conventions

- One issue per file: `docs/issues/<NN>-<slug>.md`, numbered from `01`
- The triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

## When a skill says "publish to the issue tracker"

Create a new file under `docs/issues/` following the numbering convention above (find the highest existing `NN` and increment by 1).

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly.

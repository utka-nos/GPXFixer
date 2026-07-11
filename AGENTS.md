# Repository Instructions

These instructions apply to all work in this repository. Follow them unless the user explicitly gives different instructions for the current task.

## GitHub issue identifiers

- In the rules below, `<issue-number>` is the numeric GitHub issue number.
- Format the ticket identifier as `GPX-#<issue-number>`.
- Example: GitHub issue `42` has the ticket identifier `GPX-#42`.

## Branches

- Create every new branch from `origin/main`, unless the user explicitly specifies another base.
- Before creating a branch, fetch the latest remote state when possible so that `origin/main` is current.
- For work associated with a GitHub issue, use this branch name:

  `feature/GPX-#<issue-number>`

- An optional short description may follow the required name. Use lowercase kebab-case:

  `feature/GPX-#<issue-number>-<short-description>`

- Examples:
  - `feature/GPX-#42`
  - `feature/GPX-#42-fix-track-import`

## Commits

- Every commit associated with a GitHub issue must start with its ticket identifier, followed by a space and a concise imperative description:

  `GPX-#<issue-number> <description>`

- Example: `GPX-#42 Fix track import validation`
- Commits unrelated to a GitHub issue do not require this prefix.

## Pull requests

- Open pull requests against the repository's `main` branch, unless the user explicitly specifies another base branch.
- In local Git terminology, the default base is `origin/main`.

## GitHub issue workflow

When the user asks to implement or complete a GitHub issue, perform the complete workflow below:

1. Identify the GitHub issue number.
2. Fetch the latest remote state when possible.
3. Create and check out a dedicated branch from `origin/main` using the branch naming rules above.
4. Implement and verify the requested changes in that branch.
5. Commit all task-related changes in that branch using the required commit-message prefix.

Do not reuse an existing task branch or commit issue-related changes directly to `main` unless the user explicitly requests it.

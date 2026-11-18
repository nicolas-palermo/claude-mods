# Guided view

A side panel for reviewing a pull request. Instead of reading the diff file by file, you read it by **meaning**: the changes are grouped into ideas (a feature, a fix, a refactor, the tests), and each group shows:

- a **title** that says what the change does,
- a **summary** of what was done and why, and what to look at closely,
- the **diff** of the files that change belongs to, with add and remove colors.

Hunks of one file can land in different groups, so each group shows only the lines that belong to it. A group folds and unfolds when you click its title. Titles and summaries are in Spanish.

## Use

Type in the prompt:

```
/guided-view                                   # the PR of the current branch
/guided-view https://github.com/owner/repo/pull/42
/guided-view owner/repo#42
/guided-view 42                                # a PR of the session's repo
```

With no argument it reviews the open PR of the current branch; if the branch has none, it compares the branch, uncommitted work included, against the default branch.

It reads PRs with the GitHub CLI (`gh`), signed in with `gh auth login`. Without `gh`, it can still read a public PR from its URL. The grouping is one call to Sonnet, counted in the session's usage; if it fails, the panel falls back to one group per file.

## Install

In the Claude desktop app: click **+** next to the prompt box, then **Plugins**, **Add marketplace** with `nicolas-palermo/claude-mods`, then **Add plugin** and pick `guided-view`.

At the prompt of a terminal session:

```
/plugin install guided-view --marketplace nicolas-palermo/claude-mods
```

From a shell:

```
claude plugin install guided-view --marketplace nicolas-palermo/claude-mods
```

## Develop

```
claude plugin validate guided-view
claude plugin test guided-view
```

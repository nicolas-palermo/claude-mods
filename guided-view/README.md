# Guided view

A side panel, **Guía**, that sits beside the chat and guides you through what Claude is doing:

- **Objetivo**: your latest request.
- **Plan**: Claude's task list, with a progress bar and a mark per step (✓ done, ▸ in progress, ○ pending).
- **Ahora**: the step Claude is on right now, such as "Leyendo register.tsx" or "Corriendo los tests".

It updates live as Claude works. The panel opens by itself when a session starts; if you close it, type `/guia` to open it again.

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

# MeshRoute — Agent Bootstrap Prompt

Paste this as the **first message** in any new agent session (Claude Code,
Cursor, etc.) where the project files are available on disk. It forces the
agent to load context in the right order before touching any code.

---

## Prompt to paste

```
Before doing anything else, read these project files in this exact order.
Do not write, edit, or plan any code until you have read all of them.

1. task-brief.md   — current project state, current phase, hard rules
                      summary, and testing status. This tells you WHERE
                      we are right now.
2. rules.md         — full constraints on what to build/not build, and
                      what claims not to make. This tells you the
                      BOUNDARIES.
3. architecture.md  — packet schema, module layout, backend API, routing
                      and security model. This tells you the SHAPE of
                      the system.
4. masterprompt.md  — only if you need the full phase-by-phase build
                      instructions beyond what task-brief.md summarizes.
5. testing-guide.md — only if the task involves writing or running tests,
                      or you need per-phase pass conditions in detail.

After reading, before starting the task:
- State which phase we are currently on (per task-brief.md §2).
- State any rule from rules.md that is directly relevant to the task I'm
  about to give you.
- If the task I give you would jump ahead of the current phase, or
  conflicts with a rule, flag that explicitly before proceeding — do not
  silently comply.

Once you've confirmed the above, wait for my actual task/instruction.
```

---

## Notes on using this

- If not all five files are in the agent's working directory, tell it
  which ones are missing rather than letting it proceed on partial
  context.
- Keep `task-brief.md` §2 ("Current State") up to date — this prompt is
  only as accurate as that section.
- For a quick/low-context session where you only care about one phase,
  you can shorten step 1–3 to just `task-brief.md` and skip the rest —
  but for any real coding work, keep all three (`task-brief`, `rules`,
  `architecture`) mandatory.
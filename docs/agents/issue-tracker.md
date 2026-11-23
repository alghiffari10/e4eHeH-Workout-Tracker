# Issue tracker: Linear

Issues and specs for this repo live in Linear (personal workspace: `e4eHeH`, solo developer). Use the **Linear MCP server** tools for all issue operations.

## Workspace & Workflow States

- **Workspace**: `e4eHeH` (personal workspace)
- **Lifecycle states**:
  - **Triage / Backlog**: Raw ideas or issues waiting to be scoped
  - **Todo**: Scoped and ready to be picked up
  - **In Progress**: Actively being worked on by the user or an agent
  - **In Review**: Waiting for verification / code review
  - **Done**: Completed
  - **Canceled**: Won't fix / dropped
- **Category & Triage labels**:
  - Execution / triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `hand-code`, `wontfix` (see `docs/agents/triage-labels.md`)
  - Type labels: `bug`, `improvement`, `feature`

## Conventions

Use the Linear MCP server tools for all operations:

- **Create an issue**: Use the Linear MCP issue creation tool with title, markdown description, team/project in workspace `e4eHeH`, appropriate state (e.g. `Todo` or `Backlog`), and labels (`bug`, `improvement`, `ready-for-agent`, `hand-code`, etc.).
- **Read an issue**: Fetch the issue by identifier or ID via the Linear MCP server, including its description, state, labels, comments, parent, and sub-issues.
- **List issues**: Query issues via the Linear MCP server filtered by project/team, state (open/active states), and labels.
- **Make an issue a sub-issue of a parent**: Set `parentId` to the parent issue's ID when creating or updating the sub-issue via the Linear MCP server.
- **Comment on an issue**: Add a comment to the issue using the Linear MCP comment creation tool.
- **Apply / remove labels**: Update the issue's labels via the Linear MCP issue update tool.
- **Close**: Move the issue state to `Done` (or `Canceled` when closing as `wontfix`) and leave a closing summary comment via the Linear MCP server.

## When a skill says "publish to the issue tracker"

Create a Linear issue (or parent issue with sub-issues) in the `e4eHeH` workspace using the Linear MCP server.

## When a skill says "fetch the relevant ticket"

Read the Linear issue (and any parent/sub-issues and comments) via the Linear MCP server.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single parent Linear issue with **child** sub-issues as tickets.

- **Map**: a single parent Linear issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: a Linear sub-issue linked to the map via `parentId`. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, move the ticket to `In Progress` (and assign to `e4eHeH`).
- **Blocking**: Use Linear's native blocking issue relations (`blocks` / `blocked by`). Where native relation tools aren't exposed by the MCP server, fall back to a `Blocked by: <ISSUE-ID>` line at the top of the sub-issue description. A ticket is unblocked when every blocker is in `Done` or `Canceled`.
- **Frontier query**: list the map's open sub-issues (`Todo` / `Backlog`), drop any with an open blocker or already `In Progress`; first in map order wins.
- **Claim**: Update the sub-issue state to `In Progress` via the Linear MCP server.
- **Resolve**: Post the answer as a comment on the sub-issue, move its state to `Done`, and append a context pointer (gist + issue ID) to the map issue's Decisions-so-far section.

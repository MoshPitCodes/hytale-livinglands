## Relevant Files and Folders
- CLAUDE.md
- .claude/commands
- .claude/agents
- .claude/skills
- .claude/notes
- .claude/plans
- docs/
- .github/pull_request_template.md
- /home/moshpitcodes/Development/DEV_Games/Hytale/hytale-serverlogs/
/home/moshpitcodes/Development/DEV_Games/Hytale/


## Claude Code File Locations

When creating Claude Code components, always use the correct directory:

| Component Type | Location | Purpose |
|----------------|----------|---------|
| Plans | `.claude/plans/` | Implementation plans and design documents |
| Notes | `.claude/notes/` | Session notes, research, and observations |
| Commands | `.claude/commands/` | Slash commands (workflows, processes) |
| Agents | `.claude/agents/` | Specialized domain expertise agents |
| Skills | `.claude/skills/` | Bundled resources with scripts |

**Important:**
- Never create plans, notes, or commands outside their designated directories
- Use kebab-case for all filenames (e.g., `my-new-command.md`)
- Commands are invoked as `/filename` (without `.md` extension)

## General
- Always follow the project's coding standards and style guide
- Always write clear and concise documentation for your code
- Always write unit tests for new features and bug fixes
- Always use meaningful variable and function names
- Always handle errors and exceptions gracefully

## Git Workflow
Git operations (branching, commits, PRs, releases) are managed by the `claude-git-flow-manager` agent.
See `.claude/agents/claude-git-flow-manager.md` for Git Flow conventions and commit message formats.

## Available Slash Commands

  | Command                          | Description                                    |
  | -------------------------------- | ---------------------------------------------- |
  | /structured-reflection           | Expert guide for reflection techniques         |
  | /structured-contemplation        | Expert guide for contemplation/problem-solving |
  | /claude-create-architecture-docs | Generate architecture docs with diagrams       |
  | /systematic-diagnosis            | Issue diagnosis and root cause analysis        |
  | /systematic-implementation       | Validation-driven feature implementation       |
  | /claude-ultrathink               | Deep analysis and problem solving mode         |
  | /claude-refactor-code            | Code refactoring assistance                    |

## Available Specialized Agents

  | Agent                                       | Use Case                          |
  | ------------------------------------------- | --------------------------------- |
  | mpc-nextjs-fullstack                        | Next.js 16+ App Router full-stack |
  | mpc-java-kotlin-maven-gradle-backend        | Java/Kotlin Spring backend        |
  | mpc-golang-backend-api                      | Go backend APIs                   |
  | mpc-golang-tui-bubbletea                    | Go TUI apps with Bubbletea v2     |
  | mpc-devops-infrastructure                   | DevOps, K8s, Terraform, CI/CD     |
  | mpc-react-typescript                        | React + TypeScript frontend       |
  | mpc-nixos                                   | NixOS, Nix Flakes, Home Manager   |
  | mpc-sveltekit-frontend                      | Svelte 5 + SvelteKit frontend     |
  | mpc-sveltekit-fullstack                     | SvelteKit full-stack apps         |
  | claude-code-architecture-review             | Review code for best practices    |
  | claude-security-engineer                    | Security/compliance specialist    |
  | claude-mlops-engineer                       | ML pipelines and MLOps            |
  | claude-expert-prompt-engineering            | LLM prompt optimization           |
  | claude-expert-mcp-server                    | MCP server integration            |
  | claude-expert-error-detective               | Log analysis and debugging        |
  | claude-expert-code-review                   | Code review for quality           |
  | claude-expert-agent-creation                | Creating specialized agents       |
  | claude-expert-command-creation              | Creating slash commands           |
  | claude-git-flow-manager                     | Git Flow workflows                |
  | claude-markdown-formatter                   | Markdown formatting specialist    |

## Available Skills

  | Skill             | Purpose                                     |
  | ----------------- | ------------------------------------------- |
  | artifacts-builder | Build complex React/Tailwind HTML artifacts |
  | mcp-builder       | Create MCP servers                          |
  | skill-creator     | Create new skills                           |
  | theme-factory     | Style artifacts with themes (10 presets)    |

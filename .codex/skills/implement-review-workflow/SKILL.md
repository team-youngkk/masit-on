---
name: implement-review-workflow
description: Coordinate end-to-end software changes through repository inspection, implementation planning, multi-agent task delegation, testing, independent code review, fixes, and final verification. Use when the user asks Codex to implement, build, change, refactor, or fix code and wants implementation plus review, explicitly requests this skill, or asks for work to be divided among subagents. Apply to non-trivial repository changes where separate implementation and review passes improve correctness; keep atomic edits lightweight while preserving an independent review pass when this skill is explicitly invoked.
---

# Implement Review Workflow

Deliver the requested code change, not merely a plan or review report. Keep the primary agent responsible for scope, integration, verification, and the final answer.

## 1. Establish Scope

1. Read applicable repository instructions and inspect the relevant code, tests, and current worktree state.
2. Translate the request into explicit acceptance criteria.
3. Preserve unrelated user changes. Do not let agents revert or overwrite work outside their ownership.
4. Identify risky areas such as authentication, authorization, secrets, data loss, migrations, concurrency, public APIs, and deployment behavior.
5. **Read the existing troubleshooting records before implementing.** If the repository keeps them (in this repository, `docs/troubleshooting/` with an index at `docs/troubleshooting/README.md`), find every record that touches the same files, domain, or contract as this task and treat its "근본 원인" and "재발 방지" items as binding constraints on this implementation. Do not repeat a mistake the team has already diagnosed and recorded.

Carry the constraints found in step 5 into each delegation prompt explicitly — subagents do not share this conversation's context and will not find those records on their own.

## 2. Choose an Execution Shape

Classify the change before delegating:

- Treat a change as atomic when it has one tightly coupled implementation path, touches a small localized area, and gains little from parallel work. Implement it in the primary agent.
- Treat a change as non-trivial when it crosses modules or layers, contains two or more independent workstreams, requires broad investigation, or has meaningful regression risk.

For a non-trivial change, invoke the `multi-agent-coordinator` subagent first. Ask it for a concrete execution plan containing:

- workstreams and dependencies;
- the best agent role for each workstream;
- explicit file or module ownership;
- verification responsibilities;
- integration order and likely conflict points.

Use the coordinator for planning only. The primary agent must evaluate its proposal against the repository and then launch only useful, bounded workstreams. Do not delegate merely to increase agent count.

## 3. Delegate Implementation

Run independent workstreams in parallel when concurrency is available. Use specialist roles when the task matches them; otherwise use `worker`.

Every implementation assignment must state:

- the concrete outcome and acceptance criteria;
- owned files, modules, or responsibility boundaries;
- relevant repository constraints;
- expected tests or validation;
- that the agent is not alone in the codebase, must preserve others' edits, and must adapt to concurrent changes;
- that it must report changed files, verification results, and remaining concerns.

Keep tightly coupled work with one owner. Do not assign two agents overlapping write ownership unless the second is explicitly reviewing without editing.

Continue useful primary-agent work while subagents run. Integrate results from the shared worktree, inspect the combined diff, and resolve inconsistencies centrally.

## 4. Verify the Implementation

Run the narrowest relevant checks first, followed by broader checks warranted by the change:

1. targeted tests for changed behavior;
2. type checking, compilation, or linting for affected modules;
3. broader regression tests or build when practical.

If a check cannot run, record the exact reason and use the strongest available alternative. Do not describe unrun checks as passing.

## 5. Perform Independent Review

After implementation is integrated, invoke a fresh `reviewer` subagent that did not author the changes. Give it the user request, acceptance criteria, repository instructions, and the combined diff or changed-file scope.

Ask the reviewer to focus on:

- functional correctness and unmet requirements;
- regressions, edge cases, and error handling;
- security and data integrity;
- concurrency or state-management hazards;
- maintainability issues that materially affect the change;
- missing or weak tests.

Require findings to include severity, evidence, file and line references where possible, and a concrete recommended fix. The reviewer must not edit files during this pass. Give the reviewer the troubleshooting records found in step 5 of Establish Scope so it can check whether a previously recorded mistake has recurred.

For security-sensitive changes, additionally invoke `security-auditor`. For substantial test gaps, use `test-automator` to add focused regression coverage with explicit file ownership.

### Posting review findings to a GitHub PR

This skill reviews local changes by default. When asked to review someone else's PR, or when local review findings must be published to a PR, follow these rules:

- **Post every finding as an inline review thread**, anchored to a line in the target file's diff, via `gh api graphql`'s `addPullRequestReviewThread` (or the equivalent connected GitHub tool). Findings left as PR-body or conversation comments cannot be resolved individually by the author.
- **Follow `assets/review-thread-comment-template.md` exactly.** Fill in the priority tag and all three sections (`문제 상황`, `영향`, `개선 제안`); never post with placeholders remaining.
- One finding per thread. Do not bundle multiple requests into a single thread.
- Only findings that cannot be anchored to any changed file belong in the review summary body, and the reason must be stated there.
- The review summary (`submitPullRequestReview` body) states how many findings were left and where — it does not restate the findings themselves.

## 6. Resolve Findings

Evaluate every finding against the code:

- Fix confirmed correctness, security, regression, and acceptance-criteria issues.
- Add tests for confirmed bugs when practical.
- Reject false positives explicitly with evidence.
- Assign fixes to the original owner when efficient; otherwise fix them in the primary agent.

After fixes, rerun affected checks. Request a focused reviewer recheck when fixes are material or alter behavior. Repeat until no actionable high- or medium-severity findings remain, or until progress requires authority outside the user's request.

## 7. Complete the Task

Before reporting completion:

- inspect the final diff for unintended changes;
- confirm acceptance criteria against implemented behavior;
- ensure verification results reflect the final code state;
- distinguish verified results from limitations.

Report the outcome first, then summarize important changed areas, tests run, review findings resolved, and any genuine remaining risk. Do not expose internal agent chatter or claim that delegation occurred when no subagent was used.

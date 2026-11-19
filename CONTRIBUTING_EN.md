# Code Contribution Guide

Thanks for your interest in and support of this project!

If you are confident in **Java / Vue**, contributions are very welcome. To keep code quality, collaboration efficiency, and feature traceability high, please read and follow this guide before submitting code.

---

## 1. Contribution Workflow Overview

Follow this workflow when contributing code:

1. Write a requirements document
2. Implement the back-end and front-end code
3. Run the full back-end unit test suite and front-end page smoke tests
4. Confirm the feature works and there are no known bugs
5. Commit the code and attach test screenshots
6. After review approval, the code is merged into the GitHub repository

> Do not push directly to the `main` / `master` branch. All feature changes must be submitted via a Pull Request.

---

## 2. Branch Naming

Create a personal development branch from the latest main branch:

```bash
git checkout main
git pull origin main
git checkout -b feature/your-feature-name
```

Suggested branch naming:

| Type | Example | Description |
|---|---|---|
| New feature | `feature/user-export` | Adds a new feature |
| Bug fix | `bugfix/login-error` | Fixes a defect |
| Documentation | `docs/contributing-guide` | Documentation changes |
| Refactoring | `refactor/order-service` | Code optimization without behavior change |
| Tests | `test/user-service` | Adds or modifies tests |

---

## 3. Step 1: Write the Requirements Document

Define the requirements before development to avoid wasted effort and rework.

Suggested location:

```text
docs/requirements/
```

Suggested file name:

```text
docs/requirements/feature-name.md
```

The requirements document should contain at least the following:

```markdown
# Requirement Name

## 1. Background
Explain why this requirement exists.

## 2. Goal
Describe what this requirement aims to achieve.

## 3. Scope
Describe what is included and excluded.

## 4. Feature Description
Describe the feature flow, page interactions, API changes, and data structure changes in detail.

## 5. API Design
If back-end APIs change, list the API path, HTTP method, request parameters, and response structure.

## 6. Acceptance Criteria
List verifiable acceptance conditions.

## 7. Test Plan
Describe the scope of back-end unit tests, front-end smoke tests, and key page tests.
```

---

## 4. Step 2: Implement Back-End and Front-End Code

### Back-End Java Requirements

- Follow the project's existing code structure and layering conventions.
- Keep naming clear; avoid duplicate and dead code.
- When adding or modifying an API, update the API documentation or requirements document accordingly.
- For database changes, provide a change description or migration script.
- Never commit sensitive information such as passwords, keys, tokens, or production configuration.
- Add corresponding unit tests for new business logic.

### Front-End Vue Requirements

- Follow the project's existing component, routing, and state-management conventions.
- Keep page styling consistent with the existing design.
- Avoid committing debug code, useless logs, and temporary files.
- Add necessary smoke tests or manual test notes for new pages.
- Run the project's lint and build commands before committing.

---

## 5. Step 3: Run the Full Back-End Test Suite and Front-End Smoke Tests

Testing is mandatory before committing.

### Full Back-End Unit Tests

Run according to the project's build tool:

```bash
# Maven project
mvn test

# Gradle project
./gradlew test
```

Requirements:

- The full back-end unit test suite must pass.
- New business logic must have corresponding unit tests.
- If any test fails, fix it before committing; do not skip or ignore failing cases.

### Front-End Page Smoke Tests

Run the project's scripts, for example:

```bash
npm install
npm run lint
npm run build
npm run test:smoke
```

If no automated smoke test is configured, complete at least the following manual checks:

- Pages open normally.
- Route navigation works.
- The main flow of the added or modified feature works.
- Key buttons, forms, and dialogs interact correctly.
- No obvious errors in the browser console.
- No serious styling issues on the page.

---

## 6. Step 4: Confirm the Feature Works and Has No Known Bugs

Before submitting a PR, complete self-testing and confirm:

- Acceptance criteria in the requirements document are met.
- Back-end API responses meet expectations.
- Front-end page interactions meet expectations.
- Edge cases are handled at a basic level.
- No obvious performance issues.
- No known unfixed bugs.
- No changes unrelated to this requirement are included.

If there are known limitations or unfinished items, state them clearly in the PR description.

---

## 7. Step 5: Commit Code and Attach Test Screenshots

### Commit Message Convention

Use the following format:

```text
feat: add user export feature
fix: fix verification code error on login page
docs: update contribution guide
refactor: refactor order query logic
test: add unit tests for user service
```

Common types:

| Type | Description |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation update |
| `refactor` | Refactoring |
| `test` | Test-related |
| `chore` | Miscellaneous build, dependency, or tooling changes |

### Submit a Pull Request

Push the branch:

```bash
git add .
git commit -m "feat: add user export feature"
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub and attach test screenshots in the PR description.

Suggested PR description template:

```markdown
## Requirement Description

Related requirements document:
- docs/requirements/feature-name.md

## Changes

- Back-end:
- Front-end:
- Documentation:

## Testing

### Full Back-End Unit Tests

- Command: `mvn test`
- Result: Passed
- Screenshots:

### Front-End Smoke Tests

- Command: `npm run build && npm run test:smoke`
- Result: Passed
- Page screenshots:

### Key Feature Screenshots

Attach screenshots of key pages, API test results, or a short recording of the added or modified feature.

## Known Issues

- None / describe if any

## Self-Check Checklist

- [ ] Requirements document written or updated
- [ ] Back-end and front-end code complete
- [ ] Full back-end unit tests pass
- [ ] Front-end smoke tests pass
- [ ] Feature confirmed working with no known bugs
- [ ] Test screenshots attached
- [ ] No sensitive information committed
- [ ] No unrelated changes included
```

---

## 8. Step 6: Merge into the GitHub Repository After Review

The maintainer reviews the Pull Request, focusing on:

- Whether the requirement is clear and traceable.
- Whether the code follows project conventions.
- Whether testing is sufficient and passing.
- Whether test screenshots are attached.
- Whether there are security risks or sensitive information.
- Whether unrelated changes are included.

After approval, the maintainer merges the code into the GitHub repository.

If review comments require changes, continue committing to the same branch:

```bash
git add .
git commit -m "fix: adjust implementation per review comments"
git push origin feature/your-feature-name
```

The new commits automatically update the current Pull Request.

---

## 9. Pre-Submission Self-Check Checklist

Confirm each item before submitting a PR:

- [ ] Have read this contribution guide
- [ ] Created the feature branch from the latest main branch
- [ ] Written or updated the requirements document
- [ ] Completed the back-end and front-end code
- [ ] Full back-end unit tests pass
- [ ] Front-end smoke tests pass
- [ ] Confirmed the feature works with no known bugs
- [ ] Committed the code and attached test screenshots
- [ ] PR description is complete
- [ ] No passwords, keys, tokens, or other sensitive information committed
- [ ] No unrelated changes included
- [ ] Known limitations stated in the PR

---

## 10. Notes

1. Keep each PR focused — ideally one PR solves one requirement or one issue.
2. Do not modify the main branch directly.
3. Do not commit local environment files, build artifacts, logs, or temporary files.
4. Highlight API, database, or configuration changes in the PR description.
5. Make sure test screenshots are clear and legible, proving tests were executed and passed.
6. By contributing code, you agree that your contribution will be used and distributed under the repository's license.

Thanks again for your contribution!

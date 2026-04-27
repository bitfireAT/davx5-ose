This file contains additioanl instructions for AI agents.


# Automated PR reviews

Take this section into account for automated code reviews for pull requests.

Automated reviews shall assist in catching functional bugs, logic errors, and architectural concerns. They complement (not replace) human review by the core team.

## Review focus

For automated reviews focus on:

- **Functional bugs & logic errors** – incorrect behavior, missing edge cases, null/type safety issues
- **Architectural concerns** – design patterns, maintainability, API contracts
- **Security issues** – unsafe patterns, injection risks, credential exposure
- **Code quality** – clarity, testability, redundancy

## Conventional Comments

Use [Conventional Comments](https://conventionalcomments.org/) to label review feedback. Especially make a difference between more critical labels like `issue` or `todo` and less critical ones like `suggestion`, `typo` or `polish`. Use `quibble` instead of `nitpick`. You may use the `(non-blocking)`, `(blocking)` and `(if-minor)` decorations after the label.


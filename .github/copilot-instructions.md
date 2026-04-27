This file contains instructions for AI agents.


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

Use [Conventional Comments](https://conventionalcomments.org/) to format review feedback exactly like this:

```
**<label> [decorations]:** <subject>

[optional discussion]
```

The subject should not be more than one short line/sentence. If more information is required to understand the comment, put it into the discussion part.

Use these labels:

- `issue`: Issues highlight specific problems with the subject under review.
- `suggestion`: Suggestions propose improvements to the current subject. It’s important to be explicit and clear on what is being suggested and why it is an improvement.
- `todo`: TODOs are small, trivial, but necessary changes.
- `typo`: Typo comments are like todo comments, where the main issue is a misspelling.
- `quibble`: Use that one instead of `nitpick` for trivial preference- or style-based requests. These should be non-blocking by nature.
- `polish`: Polish comments are like a suggestion, where there is nothing necessarily wrong with the relevant content, there are just some ways to immediately improve the quality.
- `note`: Notes are always non-blocking and simply highlight something the reader should take note of.

You may use decorations after the label, but only if it really improves the value:

- `(blocking)` A comment with this decoration should prevent the subject under review from being accepted, until it is resolved.
- `(non-blocking)` A comment with this decoration should not prevent the subject under review from being accepted.
- `(if-minor)` This decoration gives some freedom to the author that they should resolve the comment only if the changes end up being minor or trivial.


# Auto-generating PR descriptions

When asked to generate PR descriptions, follow the structure from `.github/pull_request_template.md`.

- Write for reviewers and future maintainers, not for code navigation
- Focus on *what changed and why*, not on specific file/line locations
- Include helpful links (issues, specs, docs) but avoid code references
- Keep descriptions clear and concise


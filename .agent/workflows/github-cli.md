---
description: GitHub CLI patterns for API calls, PR reviews, and repo exploration
---
// turbo-all

# GitHub CLI Patterns

MUST use GitHub CLI exclusively for reading GitHub content. NEVER use WebFetch for GitHub.

## Repository Metadata
```bash
gh api repos/owner/repo --jq '.description, .topics, .homepage'
gh api repos/owner/repo/releases/latest --jq '.tag_name, .published_at'
```

## File Content (base64 decode)
```bash
gh api repos/owner/repo/contents/path/to/file.md --jq '.content' | base64 -d
gh api repos/owner/repo/contents/README.md --jq '.content' | base64 -d
```

## Search and Exploration
```bash
gh api repos/owner/repo/contents/path --jq '.[] | select(.type=="file") | .name'
gh search repos "gradle hooks language:kotlin" --limit 5
gh search repos --language=kotlin --topic=gradle --limit 5
```

## Raw Content with --jq for Processing
```bash
gh api repos/owner/repo/contents/build.gradle.kts --jq '.download_url' | xargs curl -s
```

## PR Review Commands

### Get PR Comments
```bash
gh pr view PR_NUMBER --comments
gh pr view PR_NUMBER --json comments --jq '.comments[] | {author: .author.login, body: .body, createdAt: .createdAt}'
```

### Get Inline Review Comments
```bash
gh api repos/albertocavalcante/groovy-lsp/pulls/PR_NUMBER/comments --jq '.[] | {author: .user.login, path: .path, line: .line, body: .body}'
```

### Get Review Summaries
```bash
gh pr view PR_NUMBER --json reviews --jq '.reviews[] | {author: .author.login, state: .state, body: .body, submittedAt: .submittedAt}'
```

### Check PR Status
```bash
gh pr checks PR_NUMBER
```

## SonarCloud API
Get code quality issues for PR analysis:
```bash
curl "https://sonarcloud.io/api/issues/search?componentKeys=albertocavalcante_groovy-lsp&pullRequest=PR_NUMBER&types=BUG,CODE_SMELL,VULNERABILITY&statuses=OPEN,CONFIRMED"
```

## CLI Output Best Practices

When using CLI tools that output complex data, prefer structured output (JSON) and redirect to a file:

1. **Naming**: ALWAYS append a random suffix to avoid collisions (e.g., `gh_comments_82a1b.json`)
2. **Cleanup**: You MUST remove these files (`rm`) immediately after reading them

✅ Good:
```bash
gh pr view 123 --json comments > gh_comments_82a1b.json
view_file gh_comments_82a1b.json
rm gh_comments_82a1b.json
```

❌ Bad:
```bash
gh pr view 123 --comments
```

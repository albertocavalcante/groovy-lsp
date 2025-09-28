# AGENTS.md

<github-cli-ironclad>
  MUST use GitHub CLI exclusively for reading GitHub content. NEVER use WebFetch for GitHub.

# Repository metadata

gh api repos/owner/repo --jq '.description, .topics, .homepage' gh api repos/owner/repo/releases/latest --jq '.tag_name,
.published_at'

# File content (base64 decode)

gh api repos/owner/repo/contents/path/to/file.md --jq '.content' | base64 -d gh api repos/owner/repo/contents/README.md
--jq '.content' | base64 -d

# Search and exploration

gh api repos/owner/repo/contents/path --jq '.[] | select(.type=="file") | .name' gh search repos "gradle hooks
language:kotlin" --limit 5 gh search repos --language=kotlin --topic=gradle --limit 5

# Raw content with --jq for processing

gh api repos/owner/repo/contents/build.gradle.kts --jq '.download_url' | xargs curl -s </github-cli-ironclad>

<git-workflow-rules>
  <forbidden>
    NEVER commit while on main branch
  </forbidden>

  <required-workflow>
    0. git branch --show-current (verify not on main)
    1. git checkout -b new-branch-name
    2. git add file1 file2 (specific files only)
    3. git commit -m "type: description"
    4. git push -u origin new-branch-name
    5. gh pr create
  </required-workflow>

  <git-add-rules>
    NEVER: git add . or git add -A or git add -u
    ALWAYS: git add path/to/specific/file
  </git-add-rules>

  <commit-format>
    Use conventional commits: type: description
    Examples: feat:, fix:, ci:, docs:, refactor:
  </commit-format>

  <safety-check>
    Always run: git branch --show-current
    If on main: immediately git checkout -b new-branch
  </safety-check>

  <ship-it-definition>
    When user says "ship it": execute full workflow above and open PR
    Expected delivery: Pull Request created and ready for review
  </ship-it-definition>
</git-workflow-rules>

<local-git-ignore>
  File: .git/info/exclude (local ignore, not shared with others)

Add files/folders that should be ignored only on your machine: echo "filename.ext" >> .git/info/exclude echo
"folder-name/" >> .git/info/exclude

Use for: local dev files, personal notes, temp directories NOT for: files all developers should ignore (use .gitignore
instead)

  <default-behavior>
    When asked to "ignore a file" or "add to ignore": ALWAYS use .git/info/exclude
    Only use .gitignore when explicitly told "add to .gitignore"
  </default-behavior>
</local-git-ignore>

<test-debugging>
  For test debugging with println: MUST run with --info flag
  Example: ./gradlew test --tests "*SomeTest*" --console=plain --info
  Without --info flag, println output will not be visible in test results
</test-debugging>

<code-quality>
  ./gradlew lint - Check for issues (no changes)
  ./gradlew lintFix - Auto-fix all correctable lint and formatting issues
  Order: Spotless formatting first, then Detekt auto-correct
</code-quality>

<pr-review-commands>
  SonarCloud API - Get code quality issues for PR analysis:
  curl "https://sonarcloud.io/api/issues/search?componentKeys=albertocavalcante_groovy-lsp&pullRequest=PR_NUMBER&types=BUG,CODE_SMELL,VULNERABILITY&statuses=OPEN,CONFIRMED"

GitHub CLI - PR review insights: gh pr view PR_NUMBER --comments # View PR with all comments gh api
repos/owner/repo/pulls/PR_NUMBER/reviews | jq -r '.[] | "Author: \(.user.login)\nState: \(.state)\n\(.body)"' gh api
repos/owner/repo/pulls/PR_NUMBER/comments | jq -r '.[] | "File: \(.path)\nLine: \(.line)\nAuthor:
\(.user.login)\nComment:\n\(.body)"' gh pr checks PR_NUMBER # Check CI/CD status

Claude WebFetch - For automated analysis: WebFetch url="https://sonarcloud.io/api/issues/search?..." prompt="List the
issues with severity and file locations" </pr-review-commands>

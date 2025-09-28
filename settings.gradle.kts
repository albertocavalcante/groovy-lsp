plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.2"
}

gitHooks {
    preCommit {
        from {
            """
            #!/bin/sh
            # Cross-platform: sh works on all systems via Git

            # Check not on main branch (works on Windows/Mac/Linux)
            branch=${'$'}(git rev-parse --abbrev-ref HEAD)
            if [ "${'$'}branch" = "main" ]; then
                echo "ERROR: Direct commits to main branch are forbidden!"
                echo "Please create a feature branch: git checkout -b your-branch-name"
                exit 1
            fi

            echo "Running code quality checks..."
            """
        }
        // Use Gradle wrapper - works cross-platform
        tasks("spotlessCheck", "detekt")
    }

    commitMsg {
        conventionalCommits {
            // Supports: feat, fix, build, chore, ci, docs, perf, refactor, revert, style, test
            defaultTypes()
        }
    }

    createHooks(true) // Allow overwriting existing hooks
}

rootProject.name = "groovy-lsp"

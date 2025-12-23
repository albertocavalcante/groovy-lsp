# Implementation Plan

**Date:** 2025-12-23
**Status:** Active Review

## 1. Summary
The codebase is active and building successfully. However, technical debt is accumulating in metadata handling files, leading to significant code duplication (3.6% density). Test coverage is decent at 61.6% but has gaps in core AST and BSP components.

## 2. Critical Issues
*   **Duplication**: `StableStepDefinitions.kt` contains 19+ repetitions of the same string literals. `ImportUtils.kt` and `GdslToJson.kt` also have high duplication.
*   **Cognitive Complexity**: `JenkinsContextDetector.kt` and `SpockBlockIndex.kt` exceed complexity thresholds.
*   **Environment**: CI/Build requires explicit `JAVA_HOME` configuration (Java 17).

## 3. Next PR Stack (Prioritized)

### PR 1: Fix Massive Duplication in Metadata
**Goal**: Reduce code duplication and file size of `StableStepDefinitions.kt`.
*   **Target**: `groovy-jenkins/src/main/kotlin/com/github/albertocavalcante/groovyjenkins/metadata/StableStepDefinitions.kt`
*   **Action**: Extract repeated strings ("workflow-basic-steps", descriptions, etc.) into `private const val`.

### PR 2: Cleanup String Literals & Elvis Operators
**Goal**: Fix SonarCloud Critical/Major smells.
*   **Targets**:
    *   `tools/jenkins-extractor/.../GdslToJson.kt`: Extract "java.lang.Object".
    *   `groovy-lsp/.../ImportUtils.kt`: Extract "import ".
    *   `jupyter/.../ExecuteHandler.kt`: Remove useless elvis operators.

### PR 3: Enable Dependency Monitoring
**Goal**: Allow easy checking of outdated dependencies.
*   **Action**: Add `com.github.ben-manes.versions` plugin to `build.gradle.kts` (root or build-logic).

### PR 4: Reduce Cognitive Complexity
**Goal**: Improve maintainability of complex detectors.
*   **Target**: `groovy-jenkins/.../JenkinsContextDetector.kt`
*   **Action**: Decompose `detect` method into smaller helper functions.

## 4. Tech Debt Backlog
*   **Refactor**: Decompose `GroovyTextDocumentService.kt` (716 lines) - it is becoming a God Class.
*   **Testing**: Add unit tests for `AstCache.kt` and `DependencyResolver.kt` (currently 0% coverage).
*   **Performance**: Address hardcoded dispatchers in `GroovyLanguageServer.kt`.

## 5. Metrics Goals
*   **Coverage**: Target 70% (currently 61.6%).
*   **Duplication**: Target < 2% (currently 3.6%).
*   **Code Smells**: Reduce by 20 (currently 222).

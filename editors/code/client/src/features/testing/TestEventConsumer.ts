import * as vscode from 'vscode';

/**
 * Events emitted by the Gradle init script.
 */
export type TestEventType =
    | 'suiteStarted'
    | 'suiteFinished'
    | 'testStarted'
    | 'testFinished';

export interface TestEvent {
    event: TestEventType;
    id: string;
    name: string;
    parent?: string;
    result?: 'SUCCESS' | 'FAILURE' | 'SKIPPED';
    duration?: number;
    message?: string;
    stackTrace?: string;
}

/**
 * Consumes JSON test events from Gradle and updates the VS Code TestRun.
 */
export class TestEventConsumer {
    private readonly testItems: Map<string, vscode.TestItem>;

    constructor(
        private readonly run: vscode.TestRun,
        private readonly logger: vscode.OutputChannel,
    ) {
        this.testItems = new Map();
    }

    /**
     * Register a TestItem for tracking by its ID.
     */
    public registerTestItem(id: string, item: vscode.TestItem): void {
        this.testItems.set(id, item);
    }

    /**
     * Process a single line of output from Gradle.
     * Returns true if the line was a valid JSON event, false otherwise.
     */
    public processLine(line: string): boolean {
        const trimmed = line.trim();
        if (!trimmed.startsWith('{')) {
            // Not JSON, could be regular Gradle output
            this.logger.appendLine(trimmed);
            return false;
        }

        try {
            const event = JSON.parse(trimmed) as TestEvent;
            this.handleEvent(event);
            return true;
        } catch {
            // Not valid JSON, log as regular output
            this.logger.appendLine(trimmed);
            return false;
        }
    }

    private handleEvent(event: TestEvent): void {
        const item = this.testItems.get(event.id);

        switch (event.event) {
            case 'testStarted':
                if (item) {
                    this.run.started(item);
                }
                this.logger.appendLine(`[TEST] Started: ${event.name}`);
                break;

            case 'testFinished':
                if (item) {
                    this.reportTestResult(item, event);
                }
                this.logger.appendLine(
                    `[TEST] Finished: ${event.name} - ${event.result} (${event.duration}ms)`,
                );
                break;

            case 'suiteStarted':
                this.logger.appendLine(`[SUITE] Started: ${event.name}`);
                break;

            case 'suiteFinished':
                this.logger.appendLine(`[SUITE] Finished: ${event.name}`);
                break;
        }
    }

    private reportTestResult(item: vscode.TestItem, event: TestEvent): void {
        switch (event.result) {
            case 'SUCCESS':
                this.run.passed(item, event.duration);
                break;

            case 'FAILURE':
                this.run.failed(
                    item,
                    new vscode.TestMessage(event.message ?? 'Test failed'),
                    event.duration,
                );
                break;

            case 'SKIPPED':
                this.run.skipped(item);
                break;

            default:
                // Unknown result type, treat as error
                this.run.errored(
                    item,
                    new vscode.TestMessage(`Unknown test result: ${event.result}`),
                );
                break;
        }
    }
}

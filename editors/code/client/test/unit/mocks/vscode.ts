import * as sinon from 'sinon';

export const mockTasks = {
    executeTask: sinon.stub()
};

export class MockShellExecution {
    public commandLine: string;
    constructor(commandLine: string) {
        this.commandLine = commandLine;
    }
}

export class MockTask {
    public definition: any;
    public scope: any;
    public name: string;
    public source: string;
    public execution: any;

    constructor(definition: any, scope: any, name: string, source: string, execution: any) {
        this.definition = definition;
        this.scope = scope;
        this.name = name;
        this.source = source;
        this.execution = execution;
    }
}

export const vscode = {
    tasks: mockTasks,
    Task: MockTask,
    ShellExecution: MockShellExecution,
    // Add other vscode mocks here as needed
};

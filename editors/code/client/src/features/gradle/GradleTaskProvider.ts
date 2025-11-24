import * as vscode from 'vscode';
import { GradleService } from './GradleService';

export class GradleTaskProvider implements vscode.TaskProvider {
    constructor(private readonly gradleService: GradleService) {}

    async provideTasks(_token: vscode.CancellationToken): Promise<vscode.Task[]> {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders || workspaceFolders.length === 0) {
            return [];
        }

        // For simplicity, we'll just use the first workspace folder.
        // A more robust implementation might handle multiple workspaces.
        const workspaceFolder = workspaceFolders[0];
        
        try {
            const gradleTasks = await this.gradleService.getTasks(workspaceFolder.uri.toString());
            
            return gradleTasks.map(taskInfo => {
                const task = new vscode.Task(
                    { type: 'gradle', task: taskInfo.name },
                    workspaceFolder,
                    taskInfo.name,
                    'gradle',
                    new vscode.ShellExecution(`gradle ${taskInfo.name}`)
                );
                task.group = vscode.TaskGroup.Build; // Or map from taskInfo.group
                return task;
            });
        } catch (error) {
            console.error('Failed to provide Gradle tasks:', error);
            return [];
        }
    }

    // This method is called when a task is executed that this provider is supposed to handle.
    // We can return the same task definition.
    resolveTask(task: vscode.Task, _token: vscode.CancellationToken): vscode.ProviderResult<vscode.Task> {
        // We need to ensure the task has a definition and that the definition is for 'gradle'
        if (task.definition.type === 'gradle' && task.definition.task) {
            return new vscode.Task(
                task.definition,
                task.scope || vscode.workspace.workspaceFolders![0],
                task.definition.task,
                'gradle',
                new vscode.ShellExecution(`gradle ${task.definition.task}`)
            );
        }
        return undefined;
    }
}

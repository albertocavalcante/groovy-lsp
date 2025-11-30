import { ExtensionContext, commands, Disposable, window } from 'vscode';
import { restartClient, getClient } from '../server/client';
import { ExecuteCommandRequest } from 'vscode-languageclient';

/**
 * Registers all extension commands
 */
export function registerCommands(context: ExtensionContext): Disposable[] {
    const disposables: Disposable[] = [];

    // Register restart server command
    const restartServerCommand = commands.registerCommand('groovy.restartServer', async () => {
        await restartClient();
    });
    disposables.push(restartServerCommand);

    // Register show version command
    const showVersionCommand = commands.registerCommand('groovy.showVersion', async () => {
        const client = getClient();
        if (!client) {
            window.showErrorMessage('Groovy Language Server is not running');
            return;
        }

        try {
            const version = await client.sendRequest(ExecuteCommandRequest.type, {
                command: 'groovy.version',
                arguments: []
            });

            window.showInformationMessage(
                `Groovy Language Server: ${version || 'Unknown version'}`,
                'OK'
            );
        } catch (error) {
            console.error('Failed to get server version:', error);
            window.showErrorMessage(
                `Failed to get server version: ${error instanceof Error ? error.message : 'Unknown error'}`
            );
        }
    });
    disposables.push(showVersionCommand);

    // Add all disposables to context subscriptions
    context.subscriptions.push(...disposables);

    return disposables;
}
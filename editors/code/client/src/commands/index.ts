import { ExtensionContext, commands, Disposable } from 'vscode';
import { restartClient } from '../server/client';

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

    // Add all disposables to context subscriptions
    context.subscriptions.push(...disposables);

    return disposables;
}
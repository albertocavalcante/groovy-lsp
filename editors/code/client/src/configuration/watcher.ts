import { workspace, Disposable } from 'vscode';
import { affectsJavaConfiguration } from './settings';
import { restartClient } from '../server/client';

/**
 * Sets up configuration change watchers
 */
export function setupConfigurationWatcher(): Disposable {
    return workspace.onDidChangeConfiguration(async (event) => {
        // Restart server if Java configuration changed
        if (affectsJavaConfiguration(event)) {
            console.log('Java configuration changed, restarting server...');
            await restartClient();
        }

        // Add other configuration change handlers here as needed
    });
}
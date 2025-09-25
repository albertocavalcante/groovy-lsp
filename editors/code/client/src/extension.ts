/**
 * VSCode Groovy Language Extension
 * Modern, modular architecture with domain-driven design
 */

import { ExtensionContext } from 'vscode';
import { initializeClient, startClient, stopClient } from './server/client';
import { registerStatusBarItem } from './ui/statusBar';
import { registerCommands } from './commands';
import { setupConfigurationWatcher } from './configuration/watcher';

/**
 * Activates the extension
 */
export async function activate(context: ExtensionContext) {
    console.log('Groovy Language Extension is activating...');

    try {
        // Initialize the LSP client with context
        initializeClient(context);

        // Register status bar indicator
        const statusBarDisposable = registerStatusBarItem();
        context.subscriptions.push(statusBarDisposable);

        // Register commands
        registerCommands(context);

        // Setup configuration watchers
        const configWatcher = setupConfigurationWatcher();
        context.subscriptions.push(configWatcher);

        // Start the Language Server
        await startClient();

        console.log('Groovy Language Extension activated successfully');

    } catch (error) {
        const message = `Error activating Groovy Language Extension: ${error instanceof Error ? error.message : 'Unknown error'}`;
        console.error(message);
        // Don't show error message to user during activation, as it might be transient
        // The individual components will show their own error messages as needed
    }
}

/**
 * Deactivates the extension
 */
export async function deactivate(): Promise<void> {
    console.log('Deactivating Groovy Language Extension...');

    try {
        await stopClient();
        console.log('Groovy Language Extension deactivated successfully');
    } catch (error) {
        console.error('Error during deactivation:', error);
    }
}
/**
 * VSCode Groovy Language Extension
 * Modern, modular architecture with domain-driven design
 */

import { ExtensionContext } from 'vscode';
import { initializeClient, startClient, stopClient } from './server/client';
import { registerStatusBarItem } from './ui/statusBar';
import { registerCommands, initializeUpdateService } from './commands';
import { setupConfigurationWatcher } from './configuration/watcher';
import { getUpdateConfiguration } from './configuration/settings';
import { registerFormatting } from './features/formatting/formatter';
import { replService } from './features/repl';
import { registerGradleFeatures } from './features/gradle';

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

        // Initialize REPL
        replService.initialize(context);

        // Start the Language Server
        await startClient();

        // Register features that depend on the client
        registerFormatting(context);
        registerGradleFeatures(context);

        // Initialize update service for LSP version checking
        // Uses extension version from package.json (which bundles the LSP)
        const extensionVersion = context.extension.packageJSON.version as string;
        const updateService = initializeUpdateService(context, extensionVersion);
        const updateConfig = getUpdateConfiguration();
        if (updateConfig.checkOnStartup) {
            // Run async, don't block activation
            updateService.activate().catch((error) => {
                console.warn('Background update check failed:', error);
            });
        }
        context.subscriptions.push({ dispose: () => updateService.dispose() });

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
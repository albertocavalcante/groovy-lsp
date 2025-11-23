import * as vscode from 'vscode';
import { workspace, ExtensionContext, window, commands } from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    State
} from 'vscode-languageclient/node';
import { validateJava, showJavaError } from '../java/validator';
import { setClient } from '../ui/statusBar';
import { getConfiguration } from '../configuration/settings';
import { ServerResolver } from '../services/ServerResolver';

let client: LanguageClient | undefined;
let context: ExtensionContext | undefined;

export function initializeClient(extensionContext: ExtensionContext) {
    context = extensionContext;
}

/**
 * Gets the current Language Client instance
 */
export function getClient(): LanguageClient | undefined {
    return client;
}

/**
 * Gets the path to the Groovy Language Server JAR
 */
async function getServerJarPath(): Promise<string> {
    if (!context) {
        throw new Error('Extension context not initialized');
    }

    const config = getConfiguration();
    const resolver = new ServerResolver();

    try {
        return await resolver.resolve(context, { serverPath: config.serverPath });
    } catch (error) {
        const message =
            `Groovy Language Server JAR not found.\n\n` +
            `The JAR should be automatically downloaded during extension installation. ` +
            `If you have a custom path configured, please verify it exists. ` +
            `If this error persists, please report it as an issue.`;

        window.showErrorMessage(message, 'Open Issues').then(selection => {
            if (selection === 'Open Issues') {
                commands.executeCommand('vscode.open', 'https://github.com/albertocavalcante/vscode-groovy/issues');
            }
        });

        throw error;
    }
}

/**
 * Creates server options for launching the Groovy Language Server
 */
async function createServerOptions(): Promise<ServerOptions> {
    const jarPath = await getServerJarPath();
    const javaValidation = await validateJava();

    if (!javaValidation.isValid) {
        await showJavaError(javaValidation);
        throw new Error(`Java validation failed: ${javaValidation.error}`);
    }

    const javaExecutable = javaValidation.path!;

    // Server launch options
    const serverOptions: ServerOptions = {
        run: {
            command: javaExecutable,
            args: [
                '-jar',
                jarPath
            ],
            options: {
                env: process.env,
                cwd: workspace.workspaceFolders?.[0]?.uri.fsPath
            }
        },
        debug: {
            command: javaExecutable,
            args: [
                '-jar',
                jarPath
            ],
            options: {
                env: process.env,
                cwd: workspace.workspaceFolders?.[0]?.uri.fsPath
            }
        }
    };

    return serverOptions;
}

/**
 * Creates client options for the Language Client
 */
function createClientOptions(): LanguageClientOptions {
    const clientOptions: LanguageClientOptions = {
        // Register the server for Groovy and Jenkinsfile documents
        documentSelector: [
            { scheme: 'file', language: 'groovy' },
            { scheme: 'file', language: 'jenkinsfile' },
            { scheme: 'untitled', language: 'groovy' },
            { scheme: 'untitled', language: 'jenkinsfile' }
        ],
        synchronize: {
            // Notify the server about file changes to Groovy files in the workspace
            fileEvents: workspace.createFileSystemWatcher('**/*.{groovy,gvy,gy,gsh,gradle,Jenkinsfile}')
        },
        outputChannelName: 'Groovy Language Server',
        traceOutputChannel: workspace.getConfiguration('groovy').get('trace.server') !== 'off'
            ? window.createOutputChannel('Groovy Language Server Trace')
            : undefined,
        // URI converters for Windows compatibility
        uriConverters: {
            code2Protocol: (value) => {
                if (/^win32/.test(process.platform)) {
                    // Drive letters on Windows are encoded with %3A instead of :
                    // but Java doesn't treat them the same
                    return value.toString().replace("%3A", ":");
                }
                return value.toString();
            },
            protocol2Code: (value) => vscode.Uri.parse(value)
        }
    };

    return clientOptions;
}

/**
 * Starts the Language Client
 */
export async function startClient(): Promise<void> {
    if (client && client.state === State.Running) {
        return; // Already running
    }

    try {
        const serverOptions = await createServerOptions();
        const clientOptions = createClientOptions();

        client = new LanguageClient(
            'groovyLanguageServer',
            'Groovy Language Server',
            serverOptions,
            clientOptions
        );

        // Update status bar with new client
        setClient(client);

        await client.start();
        console.log('Groovy Language Server started successfully');

    } catch (error) {
        const message = `Failed to start Groovy Language Server: ${error instanceof Error ? error.message : 'Unknown error'}`;
        console.error(message);
        window.showErrorMessage(message);

        // Update status bar to reflect failure
        setClient(undefined);
        throw error;
    }
}

/**
 * Stops the Language Client
 */
export async function stopClient(): Promise<void> {
    if (!client) {
        return;
    }

    try {
        if (client.state === State.Running) {
            await client.stop();
        }
        client = undefined;
        setClient(undefined);
        console.log('Groovy Language Server stopped');
    } catch (error) {
        console.error('Error stopping Language Server:', error);
        client = undefined;
        setClient(undefined);
    }
}

/**
 * Restarts the Language Client
 */
export async function restartClient(): Promise<void> {
    try {
        await stopClient();
        await startClient();
        window.showInformationMessage('Groovy Language Server restarted successfully');
    } catch (error) {
        const message = `Failed to restart Groovy Language Server: ${error instanceof Error ? error.message : 'Unknown error'}`;
        console.error(message);
        window.showErrorMessage(message);
    }
}
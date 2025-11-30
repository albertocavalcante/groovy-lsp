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

/**
 * Jenkins shared library configuration
 */
interface JenkinsSharedLibrary {
    name: string;
    jar: string;
    sourcesJar?: string;
}

/**
 * LSP Server initialization options matching the server's configuration schema
 */
interface LspInitializationOptions {
    server: {
        maxNumberOfProblems: number;
    };
    compilation: {
        mode: 'workspace' | 'single-file';
        incrementalThreshold: number;
        maxWorkspaceFiles: number;
    };
    codenarc: {
        enabled: boolean;
        propertiesFile?: string;
        autoDetect: boolean;
    };
    jenkins: {
        filePatterns: string[];
        sharedLibraries: JenkinsSharedLibrary[];
        gdslPaths: string[];
    };
    todo: {
        scanEnabled: boolean;
        patterns: Record<string, 'Error' | 'Warning' | 'Information' | 'Hint'>;
        semanticTokensEnabled: boolean;
    };
    format: {
        enable: boolean;
    };
    trace: {
        server: 'off' | 'messages' | 'verbose';
    };
}

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
 * Builds initialization options from workspace configuration
 */
function buildInitializationOptions(): LspInitializationOptions {
    const config = workspace.getConfiguration('groovy');

    return {
        // Server configuration
        server: {
            maxNumberOfProblems: config.get<number>('server.maxNumberOfProblems', 100)
        },

        // Compilation settings
        compilation: {
            mode: config.get<'workspace' | 'single-file'>('compilation.mode', 'workspace'),
            incrementalThreshold: config.get<number>('compilation.incrementalThreshold', 50),
            maxWorkspaceFiles: config.get<number>('compilation.maxWorkspaceFiles', 500)
        },

        // CodeNarc static analysis
        codenarc: {
            enabled: config.get<boolean>('codenarc.enabled', true),
            propertiesFile: config.get<string>('codenarc.propertiesFile'),
            autoDetect: config.get<boolean>('codenarc.autoDetect', true)
        },

        // Jenkins pipeline support
        jenkins: {
            filePatterns: config.get<string[]>('jenkins.filePatterns', ['Jenkinsfile', '*.jenkins', '*.jenkinsfile']),
            sharedLibraries: config.get<JenkinsSharedLibrary[]>('jenkins.sharedLibraries', []),
            gdslPaths: config.get<string[]>('jenkins.gdslPaths', [])
        },

        // TODO/FIXME scanning
        todo: {
            scanEnabled: config.get<boolean>('todo.scanEnabled', true),
            patterns: config.get<Record<string, 'Error' | 'Warning' | 'Information' | 'Hint'>>('todo.patterns', {
                'TODO': 'Information',
                'FIXME': 'Warning',
                'XXX': 'Warning',
                'HACK': 'Warning',
                'NOTE': 'Information',
                'BUG': 'Error',
                'OPTIMIZE': 'Hint'
            }),
            semanticTokensEnabled: config.get<boolean>('todo.semanticTokensEnabled', true)
        },

        // Formatting
        format: {
            enable: config.get<boolean>('format.enable', true)
        },

        // Trace/debugging
        trace: {
            server: config.get<'off' | 'messages' | 'verbose'>('trace.server', 'off')
        }
    };
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
            fileEvents: workspace.createFileSystemWatcher('**/*.{groovy,gvy,gy,gsh,gradle,Jenkinsfile}'),
            // Also notify about configuration changes for all groovy.* settings
            configurationSection: 'groovy'
        },
        initializationOptions: buildInitializationOptions(),
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
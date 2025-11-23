import { workspace, ConfigurationChangeEvent } from 'vscode';

export interface GroovyConfiguration {
    javaHome: string | undefined;
    traceServer: 'off' | 'messages' | 'verbose';
    maxNumberOfProblems: number;
    serverPath: string | undefined;
}

/**
 * Gets the current Groovy configuration
 */
export function getConfiguration(): GroovyConfiguration {
    const config = workspace.getConfiguration('groovy');

    return {
        javaHome: config.get<string>('java.home'),
        traceServer: config.get<'off' | 'messages' | 'verbose'>('trace.server', 'off'),
        maxNumberOfProblems: config.get<number>('server.maxNumberOfProblems', 100),
        serverPath: config.get<string>('server.path')
    };
}

/**
 * Checks if a configuration change affects the specified section
 */
export function affectsConfiguration(event: ConfigurationChangeEvent, section: string): boolean {
    return event.affectsConfiguration(`groovy.${section}`);
}

/**
 * Checks if a configuration change affects Java settings
 */
export function affectsJavaConfiguration(event: ConfigurationChangeEvent): boolean {
    return affectsConfiguration(event, 'java.home');
}

/**
 * Checks if a configuration change affects server settings
 */
export function affectsServerConfiguration(event: ConfigurationChangeEvent): boolean {
    return affectsConfiguration(event, 'trace.server') ||
           affectsConfiguration(event, 'server.maxNumberOfProblems');
}
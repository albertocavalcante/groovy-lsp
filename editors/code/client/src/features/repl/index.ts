import * as vscode from 'vscode';
import { GroovyReplClient } from './GroovyReplClient';
import { getClient } from '../../server/client';

export class ReplService {
    private replClient: GroovyReplClient | undefined;
    private terminal: vscode.Terminal | undefined;
    private readonly writeEmitter = new vscode.EventEmitter<string>();
    private readonly closeEmitter = new vscode.EventEmitter<number>();

    public initialize(context: vscode.ExtensionContext) {
        context.subscriptions.push(
            vscode.commands.registerCommand('groovy.repl.start', () => this.startRepl()),
            vscode.commands.registerCommand('groovy.repl.sendLine', () => this.sendLine()),
            vscode.commands.registerCommand('groovy.repl.sendSelection', () => this.sendSelection())
        );
    }

    private async startRepl() {
        const client = getClient();
        if (!client) {
            vscode.window.showErrorMessage('Groovy Language Server is not ready.');
            return;
        }

        this.replClient = new GroovyReplClient(client);

        if (this.terminal) {
            this.terminal.show();
            return;
        }

        const pty: vscode.Pseudoterminal = {
            onDidWrite: this.writeEmitter.event,
            onDidClose: this.closeEmitter.event,
            open: () => {
                this.writeEmitter.fire('Welcome to Groovy REPL\r\n');
                this.writeEmitter.fire('Type your code in the editor and use "Send to REPL" commands.\r\n\r\n');
            },
            close: () => {
                this.terminal = undefined;
            },
            handleInput: (data: string) => {
                // Basic input handling if we wanted an interactive shell, 
                // but for now we rely on editor commands.
            }
        };

        this.terminal = vscode.window.createTerminal({
            name: 'Groovy REPL',
            pty
        });

        this.terminal.show();
    }

    private async sendLine() {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            return;
        }

        const line = editor.document.lineAt(editor.selection.active.line).text;
        await this.evaluate(line);
    }

    private async sendSelection() {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            return;
        }

        const selection = editor.document.getText(editor.selection);
        if (!selection) {
            return;
        }

        await this.evaluate(selection);
    }

    private async evaluate(code: string) {
        if (!this.replClient || !this.terminal) {
            await this.startRepl();
        }

        if (!this.replClient) {
            // If replClient is still undefined after attempting to start,
            // it means startRepl() failed (e.g., LSP not ready) and already showed an error.
            // Log this for clarity and prevent further execution.
            console.error('REPL client could not be initialized. Evaluation aborted.');
             return;
        }

        this.writeEmitter.fire(`> ${code.replaceAll('\n', '\r\n> ')}\r\n`);

        try {
            const result = await this.replClient.evaluate(code);
            if (result.output) {
                this.writeEmitter.fire(result.output.replaceAll('\n', '\r\n'));
            }
            if (result.result && result.result !== 'null') {
                this.writeEmitter.fire(`<= ${result.result}\r\n`);
            }
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            this.writeEmitter.fire(`Error: ${errorMessage}\r\n`);
        }
        
        this.writeEmitter.fire('\r\n');
    }
}

export const replService = new ReplService();

import * as vscode from "vscode";
import { State, LanguageClient } from "vscode-languageclient/node";

const TITLE = 'Groovy';
const LSP_TITLE = 'LSP';

let statusBarItem: vscode.StatusBarItem | undefined;
let currentClient: LanguageClient | undefined;

export function registerStatusBarItem(client?: LanguageClient) {
    if (statusBarItem) {
        statusBarItem.dispose();
    }

    statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    statusBarItem.text = TITLE;
    statusBarItem.show();

    if (client) {
        setClient(client);
    }

    updateView();

    return {
        dispose: () => {
            if (statusBarItem) {
                statusBarItem.dispose();
                statusBarItem = undefined;
            }
        }
    };
}

export function setClient(client: LanguageClient | undefined) {
    // Remove old event listeners
    if (currentClient) {
        // Note: We can't easily remove specific listeners, so we'll just update the reference
        currentClient = undefined;
    }

    currentClient = client;

    if (currentClient) {
        // Listen for state changes
        currentClient.onDidChangeState(() => {
            updateView();
        });
    }

    updateView();
}

function updateView() {
    if (!statusBarItem) return;
    statusBarItem.tooltip = computeTooltip();
    statusBarItem.text = computeText();
}

function computeTooltip(): vscode.MarkdownString {
    const text = new vscode.MarkdownString();
    text.isTrusted = true;
    text.supportThemeIcons = true;
    text.supportHtml = true;

    const lspState = `<div>${getLspClientStatus()}</div>`;
    text.appendMarkdown(`
<div>
<h4>Groovy Language Server</h4>
${lspState}
</div>
    `);
    return text;
}

function computeText(): string {
    const clientState = currentClient?.state ?? State.Stopped;

    switch (clientState) {
        case State.Running:
            return `$(check) ${TITLE}`;
        case State.Starting:
            return `$(sync~spin) ${TITLE}`;
        default:
            return `$(stop) ${TITLE}`;
    }
}

function getLspClientStatus(): string {
    const clientState = currentClient?.state ?? State.Stopped;
    const restartButton = `<a href="command:groovy.restartServer" title="Restart Server">$(sync)</a>`;

    switch (clientState) {
        case State.Running:
            return `$(check) ${LSP_TITLE}: Running&nbsp;&nbsp;${restartButton}`;
        case State.Starting:
            return `$(sync~spin) ${LSP_TITLE}: Starting...`;
        default:
            return `$(stop) ${LSP_TITLE}: Stopped&nbsp;&nbsp;${restartButton}`;
    }
}
import * as vscode from "vscode";

export class GradleExecutionService {
  constructor(private readonly logger: vscode.OutputChannel) { }

  public async runTests(
    _request: vscode.TestRunRequest,
    _token: vscode.CancellationToken,
  ): Promise<void> {
    this.logger.appendLine(
      "GradleExecutionService: runTests requested (not implemented)",
    );
    // Phase 3 implementation will go here
  }

  public async debugTests(
    _request: vscode.TestRunRequest,
    _token: vscode.CancellationToken,
  ): Promise<void> {
    this.logger.appendLine(
      "GradleExecutionService: debugTests requested (not implemented)",
    );
    // Phase 3/4 implementation will go here
  }
}

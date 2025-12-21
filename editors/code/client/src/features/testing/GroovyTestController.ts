import * as vscode from "vscode";
import { GradleExecutionService } from "./GradleExecutionService";

export class GroovyTestController {
  private readonly ctrl: vscode.TestController;

  constructor(
    context: vscode.ExtensionContext,
    private readonly executionService: GradleExecutionService,
  ) {
    this.ctrl = vscode.tests.createTestController(
      "groovy-test-controller",
      "Groovy Tests",
    );
    context.subscriptions.push(this.ctrl);

    this.setupRunProfiles();
  }

  private setupRunProfiles() {
    this.ctrl.createRunProfile(
      "Run",
      vscode.TestRunProfileKind.Run,
      (request, token) => this.executionService.runTests(request, token),
      true,
    );

    this.ctrl.createRunProfile(
      "Debug",
      vscode.TestRunProfileKind.Debug,
      (request, token) => this.executionService.debugTests(request, token),
      false,
    );
  }
}

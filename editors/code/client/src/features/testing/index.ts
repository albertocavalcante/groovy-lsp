import * as vscode from "vscode";
import { GradleExecutionService } from "./GradleExecutionService";
import { GroovyTestController } from "./GroovyTestController";

export function registerTestingFeatures(
  context: vscode.ExtensionContext,
  logger: vscode.OutputChannel,
) {
  const executionService = new GradleExecutionService(logger);
  new GroovyTestController(context, executionService);
}

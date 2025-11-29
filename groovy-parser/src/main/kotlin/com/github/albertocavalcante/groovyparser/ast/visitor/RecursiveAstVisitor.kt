package com.github.albertocavalcante.groovyparser.ast.visitor

import com.github.albertocavalcante.groovyparser.ast.NodeRelationshipTracker
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.BreakStatement
import org.codehaus.groovy.ast.stmt.CaseStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.ContinueStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import java.net.URI

/**
 * Recursive AST visitor that does not inherit from Groovy's ClassCodeVisitorSupport.
 * Tracks parent-child relationships via NodeRelationshipTracker while walking the tree.
 *
 * This will live alongside the legacy delegate until parity is proven.
 */
class RecursiveAstVisitor(private val tracker: NodeRelationshipTracker) {

    private lateinit var currentUri: URI

    fun visitModule(module: ModuleNode, uri: URI) {
        currentUri = uri
        tracker.clear()
        visitModuleNode(module)
    }

    private fun visitModuleNode(module: ModuleNode) {
        track(module) {
            module.imports?.forEach { visitImport(it) }
            module.starImports?.forEach { visitImport(it) }
            module.staticImports?.values?.forEach { visitImport(it) }
            module.staticStarImports?.values?.forEach { visitImport(it) }

            module.classes.forEach { visitClass(it) }
            module.statementBlock?.let { visitStatement(it) }
        }
    }

    private fun visitImport(importNode: ImportNode) {
        track(importNode) {
            visitAnnotations(importNode)
        }
    }

    private fun visitClass(classNode: ClassNode) {
        track(classNode) {
            visitAnnotations(classNode)
            classNode.fields.forEach { visitField(it) }
            classNode.properties?.forEach { visitProperty(it) }
            classNode.methods.forEach { visitMethod(it) }
            classNode.objectInitializerStatements?.forEach { visitStatement(it) }
            classNode.innerClasses.forEach { visitClass(it) }
        }
    }

    private fun visitMethod(methodNode: MethodNode) {
        track(methodNode) {
            visitAnnotations(methodNode)
            methodNode.parameters?.forEach { visitParameter(it) }
            methodNode.code?.visit(codeVisitor)
        }
    }

    private fun visitField(fieldNode: FieldNode) {
        track(fieldNode) {
            visitAnnotations(fieldNode)
            fieldNode.initialExpression?.visit(codeVisitor)
        }
    }

    private fun visitProperty(propertyNode: PropertyNode) {
        track(propertyNode) {
            visitAnnotations(propertyNode)
            propertyNode.getterBlock?.visit(codeVisitor)
            propertyNode.setterBlock?.visit(codeVisitor)
        }
    }

    private fun visitParameter(parameter: Parameter) {
        track(parameter) {
            visitAnnotations(parameter)
        }
    }

    private fun visitAnnotation(annotation: org.codehaus.groovy.ast.AnnotationNode) {
        track(annotation) {
            annotation.members?.values?.forEach { value ->
                if (value is org.codehaus.groovy.ast.expr.Expression) {
                    value.visit(codeVisitor)
                }
            }
        }
    }

    private fun visitAnnotations(node: AnnotatedNode) {
        node.annotations?.forEach { visitAnnotation(it) }
    }

    private fun visitStatement(statement: org.codehaus.groovy.ast.stmt.Statement) {
        statement.visit(codeVisitor)
    }

    private fun shouldTrack(node: ASTNode): Boolean = node.lineNumber > 0 && node.columnNumber > 0

    fun getAllNodes(): List<ASTNode> = tracker.getAllNodes()

    fun getParent(node: ASTNode): ASTNode? = tracker.getParent(node)

    fun getUri(node: ASTNode): URI? = tracker.getUri(node)

    private fun track(node: ASTNode, block: () -> Unit) {
        if (shouldTrack(node)) {
            tracker.pushNode(node, currentUri)
            try {
                block()
            } finally {
                tracker.popNode()
            }
        } else {
            block()
        }
    }

    /**
     * Visitor that walks statements/expressions recursively while tracking parents.
     */
    private val codeVisitor = object : CodeVisitorSupport() {
        override fun visitBlockStatement(block: BlockStatement) {
            track(block) { super.visitBlockStatement(block) }
        }

        override fun visitExpressionStatement(statement: ExpressionStatement) {
            track(statement) { super.visitExpressionStatement(statement) }
        }

        override fun visitReturnStatement(statement: ReturnStatement) {
            track(statement) { super.visitReturnStatement(statement) }
        }

        override fun visitThrowStatement(statement: ThrowStatement) {
            track(statement) { super.visitThrowStatement(statement) }
        }

        override fun visitIfElse(ifElse: IfStatement) {
            track(ifElse) { super.visitIfElse(ifElse) }
        }

        override fun visitForLoop(forLoop: ForStatement) {
            track(forLoop) { super.visitForLoop(forLoop) }
        }

        override fun visitWhileLoop(loop: WhileStatement) {
            track(loop) { super.visitWhileLoop(loop) }
        }

        override fun visitDoWhileLoop(loop: DoWhileStatement) {
            track(loop) { super.visitDoWhileLoop(loop) }
        }

        override fun visitTryCatchFinally(statement: TryCatchStatement) {
            // Record the try/catch node with the current parent (outer block/method)
            track(statement) {
                // no-op; just track the node itself
            }

            // Visit try block without try/catch on the stack so its parent stays the outer block
            statement.tryStatement?.visit(this)

            // Visit catches/finally with the try/catch on the stack to mirror delegate behavior
            track(statement) {
                statement.catchStatements?.forEach { visitCatchStatement(it) }
                statement.finallyStatement?.visit(this)
            }
        }

        override fun visitCatchStatement(statement: CatchStatement) {
            // Track catch node itself for parity, but visit its contents without catch on the stack
            // so that contained blocks keep TryCatchStatement as their parent.
            track(statement) {
                // no-op body; tracking only
            }
            statement.variable?.let { visitParameter(it) }
            statement.code?.visit(this)
        }

        override fun visitSwitch(statement: SwitchStatement) {
            track(statement) { super.visitSwitch(statement) }
        }

        override fun visitCaseStatement(statement: CaseStatement) {
            track(statement) { super.visitCaseStatement(statement) }
        }

        override fun visitBreakStatement(statement: BreakStatement) {
            track(statement) { super.visitBreakStatement(statement) }
        }

        override fun visitContinueStatement(statement: ContinueStatement) {
            track(statement) { super.visitContinueStatement(statement) }
        }

        override fun visitDeclarationExpression(expression: DeclarationExpression) {
            track(expression) { super.visitDeclarationExpression(expression) }
        }

        override fun visitMethodCallExpression(call: MethodCallExpression) {
            // Match legacy delegate: track the call, but only visit argument elements (not the tuple itself).
            track(call) {
                val args = call.arguments
                if (args is TupleExpression) {
                    args.expressions?.forEach { it.visit(this) }
                } else {
                    args?.visit(this)
                }
                call.objectExpression?.visit(this)
                call.method?.visit(this)
            }
        }

        override fun visitConstructorCallExpression(call: ConstructorCallExpression) {
            track(call) { super.visitConstructorCallExpression(call) }
        }

        override fun visitPropertyExpression(expression: PropertyExpression) {
            track(expression) { super.visitPropertyExpression(expression) }
        }

        override fun visitVariableExpression(expression: VariableExpression) {
            track(expression) { super.visitVariableExpression(expression) }
        }

        override fun visitConstantExpression(expression: ConstantExpression) {
            track(expression) { super.visitConstantExpression(expression) }
        }

        override fun visitClosureExpression(expression: ClosureExpression) {
            track(expression) {
                expression.parameters?.forEach { visitParameter(it) }
                super.visitClosureExpression(expression)
            }
        }

        override fun visitGStringExpression(expression: GStringExpression) {
            track(expression) { super.visitGStringExpression(expression) }
        }

        override fun visitClassExpression(expression: ClassExpression) {
            track(expression) { super.visitClassExpression(expression) }
        }

        override fun visitTupleExpression(expression: TupleExpression) {
            track(expression) { super.visitTupleExpression(expression) }
        }
    }
}

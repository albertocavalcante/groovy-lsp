package com.github.albertocavalcante.groovyjupyter.groovy

import com.github.albertocavalcante.groovyjupyter.handlers.ExecuteResult
import com.github.albertocavalcante.groovyjupyter.handlers.ExecuteStatus
import com.github.albertocavalcante.groovyjupyter.kernel.core.KernelExecutor
import com.github.albertocavalcante.groovyrepl.GroovyExecutor
import org.slf4j.LoggerFactory

class GroovyKernelExecutor : KernelExecutor {
    private val logger = LoggerFactory.getLogger(GroovyKernelExecutor::class.java)
    private val delegate = GroovyExecutor()

    override fun execute(code: String): ExecuteResult {
        logger.debug("Delegating execution to GroovyExecutor")
        val result = delegate.execute(code)

        return if (result.isSuccess) {
            ExecuteResult(
                status = ExecuteStatus.OK,
                result = result.value,
                stdout = result.stdout,
                stderr = result.stderr,
            )
        } else {
            ExecuteResult(
                status = ExecuteStatus.ERROR,
                stdout = result.stdout,
                stderr = result.stderr,
                errorName = result.errorType,
                errorValue = result.errorMessage.orEmpty(),
                traceback = result.stackTrace,
            )
        }
    }
}

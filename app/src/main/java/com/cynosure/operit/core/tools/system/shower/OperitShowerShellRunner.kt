package com.cynosure.operit.core.tools.system.shower

import com.cynosure.operit.core.tools.system.AndroidShellExecutor
import com.cynosure.operit.core.tools.system.ShellIdentity as AppShellIdentity
import com.cynosure.showerclient.ShellCommandResult
import com.cynosure.showerclient.ShellIdentity
import com.cynosure.showerclient.ShellRunner

/**
 * Bridge implementation of [ShellRunner] that delegates to the app's
 * [AndroidShellExecutor], mapping identities between the library and app.
 */
object OperitShowerShellRunner : ShellRunner {

    override suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult {
        val appIdentity = when (identity) {
            ShellIdentity.DEFAULT -> AppShellIdentity.DEFAULT
            ShellIdentity.SHELL -> AppShellIdentity.SHELL
            ShellIdentity.ROOT -> AppShellIdentity.ROOT
        }

        val result = AndroidShellExecutor.executeShellCommand(command, appIdentity)
        return ShellCommandResult(
            success = result.success,
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
        )
    }
}

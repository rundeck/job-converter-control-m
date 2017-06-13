package org.rundeck.controlm.jobs.apptypes

import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs
import org.rundeck.controlm.DocumentWriter

class OSDocumentWriter implements DocumentWriter {

    OSDocumentWriter() {}

    @Override
    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        final String cmd = ctrlm.command
        if (!cmd) {
            throw new Exception("can't create command task without command to execute")
        }

        final String interpreter = "powershell.exe -Noninteractive -Noprofile -ExecutionPolicy Bypass -Command"

        final boolean isPowershell = isPowerShellJob(ctrlm)

        if (isPowershell) {
            document.command {
                exec "$interpreter ${getPowerShellJobScriptFile(cmd)} ${getPowerShellJobArguments(cmd)}"
            }
        } else {
            document.command {
                exec(cmd)
            }
        }
    }

    private boolean isPowerShellJob(final ControlMJob ctrlm) {
        final String command = ctrlm.command

        final boolean startsWithPowerShell = command.startsWith("powershell")
        if (!startsWithPowerShell) {
            return false
        }

        final String scriptFileName = getPowerShellJobScriptFile(command);
        return scriptFileName.endsWith(".ps1")
    }

    private String getPowerShellJobScriptFile(final String command) {
        final String[] splitted = command.split(" ")

        if (splitted.size() >= 2) {
            final String second = splitted[1];
            if (second.endsWith(".ps1")) {
                return second;
            }
        }

        return "";
    }

    private String getPowerShellJobArguments(final String command) {
        final List<String> splitted = command.split(" ") as List<String>

        if (splitted.size() <= 2) {
            return "";
        }

        return splitted.subList(2, splitted.size()).join(" ")
    }

}

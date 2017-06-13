package org.rundeck.controlm.jobs.apptypes

import org.rundeck.controlm.DocumentWriter
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs

class DBDocumentWriter implements DocumentWriter, SharedFunctions {
    @Override
    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        final String scriptPath = replaceJobEnvironment(ctrlm.getSQLScriptPath(), properties)
        if (!scriptPath) {
            throw new Exception("can't create sql runner task without scriptPath")
        }

        document.command {
            'node-step-plugin'(type:'org.rundeck.sqlrunner.SQLRunnerNodeStepPlugin') {
                configuration {
                    entry(key:'jdbcDriver', value: properties['upload.jdbc.driver'])
                    entry(key:'jdbcUrl', value: properties['upload.jdbc.url'])
                    entry(key:'user', value: properties['upload.jdbc.user'])
                    entry(key:'password', value: properties['upload.jdbc.password'])
                    entry(key:'commit', value: properties['upload.jdbc.commit'])
                    entry(key:'scriptPath', value: scriptPath)
                }
            }
        }

    }
}

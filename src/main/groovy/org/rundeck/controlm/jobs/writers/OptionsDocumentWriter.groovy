package org.rundeck.controlm.jobs.writers

import org.rundeck.controlm.DocumentWriter
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs

class OptionsDocumentWriter implements DocumentWriter {
    @Override
    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        if (!ctrlm.options) {
            return;
        }

        document.context {
            options(preserveOrder: true) {
                ctrlm.options.each { name, value ->
                    option(name: name, value: value)
                }
            }
        }

    }
}

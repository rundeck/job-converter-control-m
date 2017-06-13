package org.rundeck.controlm.jobs.writers

import org.rundeck.controlm.DocumentWriter
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs

class JobDocumentWriter implements DocumentWriter {

    private final OptionsDocumentWriter optionsJobWriter
    private final ScheduleDocumentWriter scheduleDocumentWriter
    private final SequenceDocumentWriter sequenceDocumentWriter

    JobDocumentWriter() {
        this.optionsJobWriter = new OptionsDocumentWriter()
        this.scheduleDocumentWriter = new ScheduleDocumentWriter()
        this.sequenceDocumentWriter = new SequenceDocumentWriter()
    }

    @Override
    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        document.loglevel("INFO")
        document.executionEnabled(ctrlm.executionEnabled)

        document.name(ctrlm.name);
        document.description(ctrlm.description);

        document.group(ctrlms.getGroup(ctrlm));

        // what is this for?
        document.dispatch {
            excludePrecedence true
            keepgoing false
            rankOrder 'ascending'
            threadcount 1
        }

        // node configuration
        final String node = ctrlm.nodeId
        if (node) {
            document.nodefilters {
                final String username = ctrlm.owner
                filter "job-host: ${node}"
            }
        } else {
            println "job: ${ctrlm.name} doesn't have a valid nodeId, raw node id is: ${ctrlm.rawNodeId}"
        }

        document.nodesSelectedByDefault true

        scheduleDocumentWriter.write(document, ctrlm, ctrlms, properties)
        optionsJobWriter.write(document, ctrlm, ctrlms, properties)
        sequenceDocumentWriter.write(document, ctrlm, ctrlms, properties)

    }
}


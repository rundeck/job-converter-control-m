package org.rundeck.controlm

import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs

interface DocumentWriter {

    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties);

}

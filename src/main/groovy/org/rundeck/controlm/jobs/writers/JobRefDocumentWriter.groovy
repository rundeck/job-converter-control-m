package org.rundeck.controlm.jobs.writers

import org.rundeck.controlm.DocumentWriter
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs;

public class JobRefDocumentWriter implements DocumentWriter {

    @Override
    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        final Map<String, Set<ControlMJob>> jobsByPostConditions = ctrlms.getJobsActivatedByJob(ctrlm)

        jobsByPostConditions.each { final String postCondition, final Set<ControlMJob> jobs ->
            jobs.each { final ControlMJob job ->
                document.command {
                    jobref(name: job.name, group: ctrlms.getGroup(job), nodeStep: 'true')
                }
            }
        }
    }



}

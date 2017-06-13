package org.rundeck.controlm.jobs

class ControlMJobs extends ArrayList<ControlMJob> {

    private final Map<ControlMJob, String> groupsByJob;
    private final Map<Set<String>, ControlMJob> jobsByPreConditions;

    ControlMJobs(final Collection<? extends ControlMJob> col) {
        super(col)

        this.groupsByJob = col.collectEntries { final ControlMJob job ->
            return [job, job.group]
        }

        this.jobsByPreConditions = col.collectEntries { final ControlMJob job ->
            return [job.preConditions, job]
        }
    }

    String getGroup(final ControlMJob job) {
        groupsByJob[job]
    }

    Map<Set<String>, ControlMJob> getJobsByPreConditions() {
        jobsByPreConditions
    }

    Map<String, Set<ControlMJob>> getJobsActivatedByJob(final ControlMJob job) {
        return job.postConditions.collectEntries { final String postCondition ->

            final Set<Set<String>> preconditionsTriggeredByPostCondition = jobsByPreConditions.keySet()
                    .grep { final Set<String> preCondition -> preCondition.contains(postCondition) }

            final Set<ControlMJob> jobsTriggeredByPostCondition = preconditionsTriggeredByPostCondition
                    .collect { final Set<String> precondition -> jobsByPreConditions.get(precondition) }

            return [postCondition, jobsTriggeredByPostCondition]
        }.findAll { final Map.Entry<String, Set<ControlMJob>> entry ->
            final Set<ControlMJob> activatedJobs = entry.value
            return !activatedJobs.isEmpty()
        }
    }

}

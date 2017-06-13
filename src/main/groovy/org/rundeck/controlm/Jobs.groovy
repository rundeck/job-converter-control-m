package org.rundeck.controlm

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobParser
import org.rundeck.controlm.jobs.ControlMJobs
import org.rundeck.controlm.jobs.writers.JobDocumentWriter

class Jobs extends Base {

    private static final Set<AppType> APP_TYPES = EnumSet.of(AppType.DATABASE, AppType.FILE_WATCH, AppType.OS, AppType.FILE_TRANS)
    private static final JobDocumentWriter JOB_DOCUMENT_WRITER = new JobDocumentWriter()

    public static void main(final String... args) {
        final List<File> inputFiles = getInputFileFromArguments(args)
        final Properties properties = getConfig()

        final ControlMJobParser parser = new ControlMJobParser(properties, APP_TYPES)

        final Map<File, ControlMJobs> ctrlmsByFile = inputFiles.collectEntries { file ->
            final ControlMJobs ctrlms = parser.parse(file)
            return [file, ctrlms]
        }

        if (!ctrlmsByFile) {
            printlnAndExit "no ControlMJobs after parsing", 0
        }

        final File outputDir = getOutputDir();
        final Map<String, String> outputFileGroups = getOutputFileGroups(properties)


        if (!outputDir.exists() && !outputDir.mkdirs()) {
            printlnAndExit "couldn´t create directory: ${outputDir}"
        }

        println "output directory will be: ${outputDir}"

        ctrlmsByFile.each { File file, ControlMJobs ctrlms ->
            // print every
            (new File(outputDir, file.name)) << XmlUtil.serialize(buildJobsDocument(ctrlms, properties))

            // print by outputGroup
            final Map<String, List<ControlMJob>> ctrlmsByOutputGroup = ctrlms.groupBy { job -> outputFileGroups.get(job.rawGroup, "GROUPLESS") }

            ctrlmsByOutputGroup.each { String outputGroup, List<ControlMJob> jobsInGroup ->
                final String fileName = "${file.name.replaceAll(".xml", "")}_${outputGroup}.xml"
                final ControlMJobs jobs = new ControlMJobs(jobsInGroup)

                (new File(outputDir, fileName)) << XmlUtil.serialize(buildJobsDocument(jobs, properties))
            }
        }
    }

    private static Map<String, String> getOutputFileGroups(final Properties properties) {
        final String filePath = properties['c2r.jobs.outputFileGroups']
        if (!filePath) {
            return [:]
        }

        final File groupFile = new File(filePath);

        def groups = [:];

        groupFile.eachLine { line ->
            if (line.trim()) {
                def (key, value) = line.split(',').collect { it.trim().toLowerCase() }
                groups."$key" = value
            }
        }

        return groups;
    }

    private static Object buildJobsDocument(final ControlMJobs ctrlms, final Properties properties) {
        def markupBuilder = new StreamingMarkupBuilder();

        return markupBuilder.bind { document ->
            document.joblist {
                ctrlms.each { ctrlm ->
                    document.job {
                        JOB_DOCUMENT_WRITER.write(document, ctrlm, ctrlms, properties)
                    }
                }
            }
        }
    }

}




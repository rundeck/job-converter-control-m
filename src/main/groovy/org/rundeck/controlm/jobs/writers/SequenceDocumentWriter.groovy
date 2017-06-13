package org.rundeck.controlm.jobs.writers

import org.rundeck.controlm.AppType
import org.rundeck.controlm.DocumentWriter
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs
import org.rundeck.controlm.jobs.apptypes.DBDocumentWriter
import org.rundeck.controlm.jobs.apptypes.FTPDocumentWriter
import org.rundeck.controlm.jobs.apptypes.FWDocumentWriter
import org.rundeck.controlm.jobs.apptypes.OSDocumentWriter;

class SequenceDocumentWriter implements DocumentWriter {

    private final JobRefDocumentWriter jobRefDocumentWriter
    private final Map<AppType, DocumentWriter> documentWriterByAppType

    SequenceDocumentWriter() {
        this.jobRefDocumentWriter = new JobRefDocumentWriter()

        this.documentWriterByAppType = [
                (AppType.DATABASE): new DBDocumentWriter(),
                (AppType.FILE_TRANS): new FTPDocumentWriter(),
                (AppType.FILE_WATCH): new FWDocumentWriter(),
                (AppType.OS): new OSDocumentWriter()
        ]
    }

    @Override
    void write(final document,
               final ControlMJob ctrlm,
               final ControlMJobs ctrlms,
               final Properties properties) {
        final AppType appType = ctrlm.appType

        final Map<String, Set<ControlMJob>> activatedJobs = ctrlms.getJobsActivatedByJob(ctrlm)
        final String strategy = activatedJobs.size() > 1 ? 'parallel' : 'node-first'

        document.sequence(keepgoing: false, strategy: strategy) {
            final DocumentWriter documentWriter = documentWriterByAppType[appType]
            documentWriter.write(document, ctrlm, ctrlms, properties)

            jobRefDocumentWriter.write(document, ctrlm, ctrlms, properties)
        }
    }
}

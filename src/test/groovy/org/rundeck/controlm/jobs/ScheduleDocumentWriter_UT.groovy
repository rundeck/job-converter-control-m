package org.rundeck.controlm.jobs

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.junit.Before
import org.junit.Test
import org.rundeck.controlm.jobs.writers.ScheduleDocumentWriter

class ScheduleDocumentWriter_UT {

    // class under test
    ScheduleDocumentWriter scheduleDocumentWriter

    // support
    ControlMJobParser parser

    @Before
    void before() {
        scheduleDocumentWriter = new ScheduleDocumentWriter()
        parser = new ControlMJobParser()
    }

    @Test
    void test_last_monday_of_every_month() {
        final payload = this.getClass().getResourceAsStream("job_that_runs_last_monday_of_every_month.xml")
        final ControlMJobs ctrlms = parser.parse(payload);

        def markupBuilder = new StreamingMarkupBuilder();

        def xml = markupBuilder.bind { document ->
            ctrlms.each { ctrlm ->
                document.job {
                    scheduleDocumentWriter.write(document, ctrlm, ctrlms, new Properties())
                }
            }
        }

        println XmlUtil.serialize(xml);

    }

}

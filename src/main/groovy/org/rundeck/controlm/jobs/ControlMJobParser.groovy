package org.rundeck.controlm.jobs

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChild
import org.rundeck.controlm.AppType
import org.xml.sax.XMLReader
import org.xml.sax.helpers.XMLReaderFactory

class ControlMJobParser {

    private final Properties properties;
    private final Closure<Boolean> filter;

    ControlMJobParser() {
        this(new Properties())
    }

    ControlMJobParser(final Properties properties) {
        this.properties = properties
        this.filter = { it -> true }
    }

    ControlMJobParser(final Properties properties, final Set<AppType> appTypes) {
        this.properties = properties
        this.filter = { ControlMJob job -> job.appType in appTypes }
    }

    ControlMJobs parse(final input) {
        if (!input) return []

        final GPathResult rootNode = parseXML(input)
        final List<NodeChild> nodes = getXMLJobs(rootNode)

        final Properties config = properties

        final List<ControlMJob> jobs = nodes.collect {
            node -> new ControlMJob(node, config)
        }.grep(filter)

        return new ControlMJobs(jobs)
    }

    private GPathResult parseXML(final input) {
        final XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        return new XmlSlurper(xmlReader).parse(input)
    }

    private List<NodeChild> getXMLJobs(final GPathResult rootNode) {
        return rootNode.'**'.findAll { node -> node.name() == 'JOB' }
    }

}

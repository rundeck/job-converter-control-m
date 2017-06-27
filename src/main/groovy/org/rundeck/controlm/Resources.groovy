package org.rundeck.controlm

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobParser

class Resources extends Base {

    public static void main(String... args) {
        final List<File> inputFiles = getInputFileFromArguments(args)
        final Properties properties = getConfig();

        final ControlMJobParser parser = new ControlMJobParser(properties, EnumSet.of(AppType.OS))
        final List<ControlMJob> ctrlms = inputFiles.collect { file -> parser.parse(file) }.flatten()

        if (!ctrlms) {
            printlnAndExit "no controlm jobs were available, after parsing every file"
        }

        final File outputDir = new File("output/resources");//getOutputDir();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            printlnAndExit "couldn´t create directory: ${outputDir}"
        }

        println "output directory will be: ${outputDir}"

        final Map<String, Set<String>> ownersByNodeId = getOwnersByNodeId(ctrlms)

        def markupBuilder = new StreamingMarkupBuilder();

        def resources = markupBuilder.bind { builder ->
            builder.project {
                ownersByNodeId.each { nodeId, owners ->
                    owners.each { owner  ->
                        final String nodeName = "${owner}@${nodeId}";

                        node(name: nodeName,
                                description: "${owner} at ${nodeId}",
                                tags: "",
                                hostname: nodeId,
                                osArch: "x86_64",
                                osFamily: "",
                                osName:"",
                                username: owner)
                    }
                }
            }
        }

        (new File(outputDir, "resources.xml")) << XmlUtil.serialize(resources)
    }

    static Map<String, Set<String>> getOwnersByNodeId(final List<ControlMJob> ctrlms) {
        if (!ctrlms) {
            return [:]
        }

        final Map<String, List<ControlMJob>> ctrlmsByNodeId = ctrlms.groupBy { ctrlm -> ctrlm.nodeId }

        return ctrlmsByNodeId.collectEntries { id, List<ControlMJob> jobs ->
            final Set<String> owners = jobs.collect { job -> job.owner } as Set<String>
            return [id, owners]
        }
    }


}


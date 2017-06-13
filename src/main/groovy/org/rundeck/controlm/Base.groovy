package org.rundeck.controlm

class Base {

    static printlnAndExit(message, errCode = 1) {
        println message
        System.exit errCode
    }

    static printlnAndException(message, exception = new Exception()) {
        println message
        throw exception
    }

    static getOutputDir() {
        def outputDirName = new Date().format("YYYY_MM_dd_HH_mm'/'")
        def outputDir = new File(outputDirName)

        return outputDir
    }

    static Properties getConfig() {
        final File propertiesFile = new File("rundeck-controlm.properties");
        if (!propertiesFile.exists() || !propertiesFile.isFile()) {
            printlnAndExit "can't find properties file";
        }

        final Properties properties = new Properties();
        propertiesFile.withInputStream { properties.load(it) }

        return properties;
    }

    static List<File> getInputFileFromArguments(args) {
        if (!args) {
            printlnAndExit "please supply a valid control-m XML file"
        }

        final List<File> inputFiles = args.grep { it -> it.endsWith ".xml" }
                .collect { it -> new File(it) }
                .grep { it -> it.exists() && it.isFile() }

        if (!inputFiles) {
            printlnAndExit "please supply one or more xml Files"
        }

        return inputFiles
    }

}

package org.rundeck.controlm.jobs

trait SharedFunctions {

    String cleanNodeNames(String node) {
        node = (node.split("\\.").size() > 1 ? node.split("\\.")[0] : node);
        node = node.replace("CTRLM","RDECK")
                .replace("FAST","RDECK")

        return (node.size() > 4 ? node.substring(4):node)
    }

    String replaceEnvironment(final String input) {//Deactivated for now
        return input;//.replaceAll("PRODUCTION", '\\$\\{globals.environment.domainname\\}')
        //.replaceAll("production", '\\$\\{globals.environment.domainname\\}')
    }

}

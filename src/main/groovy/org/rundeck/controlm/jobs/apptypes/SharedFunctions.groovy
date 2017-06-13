package org.rundeck.controlm.jobs.apptypes


trait SharedFunctions {

    String replaceJobEnvironment(final String input, final Properties properties) {
        input.replace(properties['c2r.jobs.environment'].toString(),'${globals.environment.name}')
    }

}

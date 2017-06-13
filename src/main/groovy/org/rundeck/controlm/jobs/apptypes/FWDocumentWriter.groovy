package org.rundeck.controlm.jobs.apptypes

import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs
import org.rundeck.controlm.DocumentWriter

class FWDocumentWriter implements DocumentWriter, SharedFunctions {

    @Override
    void write(final document, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        final Map optionsMap = ctrlm.options
        final String filePath = optionsMap['FileWatch-FILE_PATH']

        if (!filePath) {
            throw new Exception("can't create file watch task without path")
        }

        final useGrepPlugin = properties['c2r.FileWaitPlugin.hasString']
        if (useGrepPlugin) {
            document.command {
                'node-step-plugin'(type:'PS1-File-Grep') {
                    configuration {
                        entry(key:'filePath', value: replaceJobEnvironment(filePath,properties));
                        entry(key:'interval', value: properties['c2r.FileWaitPlugin.interval']);
                        entry(key:'maxTries', value: properties['c2r.FileWaitPlugin.maxTries']);
                        entry(key:'hasString', value: properties['c2r.FileWaitPlugin.hasString']);
                    }
                }
            }
        } else {
            document.command {
                'node-step-plugin'(type:'PS1-File-Wait') {
                    configuration {
                        entry(key:'filePath', value: replaceJobEnvironment(filePath,properties));
                        entry(key:'interval', value: properties['c2r.FileWaitPlugin.interval']);
                        entry(key:'maxTries', value: properties['c2r.FileWaitPlugin.maxTries']);
                    }
                }
            }
        }
    }

}

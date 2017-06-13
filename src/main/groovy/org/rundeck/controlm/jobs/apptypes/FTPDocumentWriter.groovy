package org.rundeck.controlm.jobs.apptypes

import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs
import org.rundeck.controlm.DocumentWriter

class FTPDocumentWriter implements DocumentWriter, SharedFunctions {

    @Override
    void write(final rdeck, final ControlMJob ctrlm, final ControlMJobs ctrlms, final Properties properties) {
        final Map<String, String> options = ctrlm.options

        if (!options['FTP-CONNTYPE1']) {
            options['FTP-CONNTYPE1'] = "FTP"
        }
        if (!options['FTP-CONNTYPE2']) {
            options['FTP-CONNTYPE2'] = "FTP"
        }
        if (!options['FTP-LHOST']) {
            throw new Exception("can't create file transfer runner task without FTP-LHOST")
        }
        if (!options['FTP-RHOST']) {
            throw new Exception("can't create file transfer runner task without FTP-RHOST")
        }
        if (!options['FTP-LUSER']) {
            throw new Exception("can't create file transfer runner task without FTP-LUSER")
        }
        if (!options['FTP-RUSER']) {
            throw new Exception("can't create file transfer runner task without FTP-RUSER")
        }
        if (!options['FTP-LPATH1']) {
            throw new Exception("can't create file transfer runner task without FTP-LPATH1")
        }
        if (!options['FTP-RPATH1']) {
            throw new Exception("can't create file transfer runner task without FTP-RPATH1")
        }

        final String sourceURL = replaceJobEnvironment(getSourceURL(options), properties)
        final String destionationURL = replaceJobEnvironment(getDestinationURL(options), properties)

        rdeck.command {
            'node-step-plugin'(type:'filetransfer') {
                configuration {
                    entry(key:'sourceURLString', value: sourceURL)
                    entry(key:'sourceUsername', value: options['FTP-LUSER'])
                    entry(key:'sourcePassword', value: 'USE_KEYSTORE')
                    entry(key:'destURLString', value: destionationURL)
                    entry(key:'destUsername', value: options['FTP-RUSER'])
                    entry(key:'destPassword', value: 'USE_KEYSTORE')
                }
            }
        }
    }

    private String getSourceURL(final Map<String, String> options) {
        final String sourceURLProtocol = (options['FTP-CONNTYPE1'] == 'LOCAL' ? 'SFTP' : options['FTP-CONNTYPE1'])
        final String sourceURL = sourceURLProtocol + "://" + options['FTP-LHOST'] + "/" + options['FTP-LPATH1']

        return sourceURL
    }

    private String getDestinationURL(final Map<String, String> options) {
        final String destinationURLProtocol = (options['FTP-CONNTYPE2'] == 'LOCAL' ? 'SFTP' : options['FTP-CONNTYPE2'])
        final String destionationURL = destinationURLProtocol + "://" + options['FTP-RHOST'] + "/" + options['FTP-RPATH1']

        return destionationURL
    }

}

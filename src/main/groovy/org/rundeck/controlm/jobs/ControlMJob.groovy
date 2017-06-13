package org.rundeck.controlm.jobs

import groovy.util.slurpersupport.NodeChild
import org.rundeck.controlm.AppType
import org.rundeck.controlm.jobs.parsers.CountryParser
import org.rundeck.controlm.jobs.parsers.OptionsParser

class ControlMJob implements SharedFunctions {

    private final NodeChild ctrlm;
    private final Properties properties

    private final Map<String, String> parsedOptions
    private final List<String> parsedCountries

    ControlMJob(final NodeChild ctrlm, final Properties properties) {
        this.ctrlm = ctrlm
        this.properties = properties

        final OptionsParser optionsParser = new OptionsParser(properties)
        this.parsedOptions = optionsParser.getOptions(ctrlm)

        final CountryParser countryParser = new CountryParser(properties)
        final String memName = ctrlm['@MEMNAME']?.text()?.trim() ?: ''
        this.parsedCountries = countryParser.parse(memName)
    }

    String getName() {
        final String jobName = ctrlm['@JOBNAME'].text();

        if (jobName.split("PRODJ-").size() > 1) {
            return jobName.split("PRODJ-")[1]
        }

        return jobName;
    }

    String getDescription() {
        return ctrlm['@DESCRIPTION'].text()
    }

    String getExecutionEnabled() {
        return properties['c2r.jobs.executionEnabled']
    }

    String getOwner() {
        return replaceEnvironment(ctrlm['@OWNER'].text())
    }

    String getNodeId() {
        String node = ctrlm['@NODEID'].text().toUpperCase()
        return cleanNodeNames(node)
    }

    String getRawNodeId() {
        ctrlm['@NODEID'].text().toUpperCase()
    }

    AppType getAppType() {
        final String token = ctrlm['@APPL_TYPE']?.text()?.trim()
        return AppType.getByToken(token).orElse(AppType.OS)
    }


    String getCountry() {
        if (!parsedCountries) {
            return properties['c2r.jobs.defaultCountry']
        }

        return parsedCountries.first()
    }

    String getGroup() {
        final String country = getCountry()
        final String application = ctrlm['@APPLICATION'].text()
        final String group = ctrlm['@GROUP'].text();

        return "${country}/${application}/${group}"
    }

    String getRawGroup() {
        return ctrlm['@GROUP'].text().toLowerCase()
    }

    Set<String> getPreConditions() {
        return ctrlm.'**'
                .grep { it -> it.name() == 'INCOND' }
                .collect { it['@NAME'].text() }
    }

    Set<String> getPostConditions() {
        return ctrlm.'**'
                .grep { it -> it.name() == 'OUTCOND' }
                .grep { it -> it['@SIGN']?.text() == 'ADD'}
                .collect {
                    final String text = it['@NAME'].text()
                    return text
                }
    }

    Map<String, String> getOptions() {
        return parsedOptions
    }

    String getCommand() {
        final String cmd = ctrlm['@CMDLINE']?.text() ?: ctrlm['@INSTREAM_JCL']?.text()

        return cmd.replaceAll(/%%(\w+)/, { it -> String.format('${globals.%s}', it[1]) })
                .replaceAll(properties['c2r.jobs.environment'].toString(), { it -> '${globals.environment.name}' })
    }

    String getSQLScriptPath() {
        final Map options = getOptions()

        final String scriptPath = options['DB-SCRPT_NAME'] ?: ''
        return scriptPath.replaceAll(properties['c2r.jobs.environment'].toString(), { it -> '${globals.environment.name}' })
    }

    String getMonthSchedule() {
        final List<String> months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
                                     'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']

        final List<String> activeMonths = months.grep {
            month -> ctrlm["@${month}"].text() == '1'
        }

        return activeMonths.size() == 12 ? "*" : activeMonths.join(",")
    }

    boolean isIgnoreSchedule() {
        final boolean confirmFlagIsOn = ctrlm['@CONFIRM']?.text() == "1"
        return confirmFlagIsOn
    }

    String getCyclic() {
        return ctrlm['@CYCLIC']?.text()
    }

    boolean isScheduleEnabled() {
        return getCyclic() == "1"
    }

    String getTimeFrom() {
        return ctrlm['@TIMEFROM']?.text() ?: '0000'
    }

    String getTimeTo() {
        return ctrlm['@TIMETO']?.text().replaceAll(/\D/, '') ?: ''
    }

    String getInterval() {
        return ctrlm['@INTERVAL']?.text()
    }

    String getDays() {
        return ctrlm["@DAYS"]?.text()
    }

    String getDaysAndOr() {
        return ctrlm["@DAYS_AND_OR"]?.text()
    }

    String getWeekDays() {
        return ctrlm["@WEEKDAYS"]?.text()
    }

    @Override
    public String toString() {
        return "ControlMJob{" +
                "nodeId='" + getNodeId() + '\'' +
                ", name='" + getName() + '\'' +
                ", appType=" + getAppType() +
                '}';
    }

}

package org.rundeck.controlm.jobs.parsers

import groovy.util.slurpersupport.NodeChild
import org.rundeck.controlm.jobs.SharedFunctions

import java.util.regex.Pattern;

class OptionsParser implements SharedFunctions {

    private static final Map<Pattern, String> REMOVED_OPTIONS_PARSER_REPLACEMENTS = [
            (~/^%%/): "",
            (~/^\*.*\*$/): ""
    ]

    private final Properties properties

    OptionsParser(final Properties properties) {
        this.properties = properties
    }

    Map<String, String> getOptions(final NodeChild ctrlm) {
        final Map<String, String> options = ctrlm.'**'
                .grep { it -> ("AUTOEDIT2" == it.name() || "AUTOEDIT" == it.name()) }
                .collectEntries { it ->
            if ("AUTOEDIT2" == it.name()) {
                def name = it['@NAME'].text();
                def filtered = name.replaceAll(~/%/, '');

                return [filtered, it['@VALUE'].text()]
            } else if (it['@EXP'].text().split("=").size() > 1) {
                def name = it['@EXP'].text().split("=")[0];
                def filtered = name.replaceAll(~/%/, '');
                return [filtered, it['@EXP'].text().split("=")[1]]
            }
        }

        final Set<String> excluded = getExcludedOptions()

        return options
                .findAll { it -> !excluded.contains(it.key) }
                .collectEntries { key, value ->
                    if (key.contains("host") || key.contains("HOST")) {
                        return [key, replaceEnvironment(cleanNodeNames(value))]
                    }

                    return [key, replaceEnvironment(value)]
                }
    }

    Set<String> getExcludedOptions() {
        final String filePath = properties['c2r.jobs.excludedOptions'] ?: '';
        if (!filePath) {
            return [] as Set<String>
        }

        final File file = new File(filePath);
        if (null == file || !file.exists() || !file.isFile()) {
            println "file: ${filePath} is not a file"
            return [] as Set<String>
        }

        try {
            final List<String> lines = file.readLines()
            if (!lines) {
                return [] as Set<String>
            }

            return lines.collect { final String line ->
                return REMOVED_OPTIONS_PARSER_REPLACEMENTS.inject(line, { final String current,
                                                                          final Pattern pattern,
                                                                          final String replacement ->
                    return pattern.matcher(current).replaceAll(replacement)
                })
            }.collect { final String line ->
                return line.trim()
            }.grep { final String line  ->
                return !line.isEmpty()
            } as Set<String>
        } catch (final IOException ex) {
            println "exception: ${ex.message} while reading file: $filePath"
            ex.printStackTrace(System.out)
            return [] as Set<String>
        }
    }

}

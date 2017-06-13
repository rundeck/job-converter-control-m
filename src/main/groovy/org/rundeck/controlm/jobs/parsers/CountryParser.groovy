package org.rundeck.controlm.jobs.parsers

import java.util.regex.Pattern

class CountryParser {

    private static final Pattern COUNTRY_PATTERN = ~/([-_]|\b)([A-Z]{2})([-_]|\b)/

    private final Properties properties;

    CountryParser() {
        this(new Properties())
    }

    CountryParser(final Properties properties) {
        this.properties = properties
    }

    List<String> parse(final String input) {
        if (!input) {
            return []
        }

        final Set<String> excluded = getExcludedCountries();
        final List<String> countries = parseCountries(input)

        return countries.grep { !excluded.contains(it) }
    }

    private Set<String> getExcludedCountries() {
        final String excludedCountries = properties['c2r.jobs.excludedCountries']

        if (!excludedCountries) {
            return [] as Set<String>
        }

        return excludedCountries.split(",") as Set<String>
    }

    private List<String> parseCountries(final String text) {
        return (text =~ COUNTRY_PATTERN).collect { it -> it[2] }
    }

}

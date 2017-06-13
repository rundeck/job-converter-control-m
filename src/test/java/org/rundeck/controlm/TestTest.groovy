package org.rundeck.controlm

import groovy.util.slurpersupport.GPathResult

class TestTest {

    def NUMERIFIED_DEPENDENCIES = [:]

    String numerifyDependecies(final String input) {
        NUMERIFIED_DEPENDENCIES.computeIfAbsent(input, { it ->
            NUMERIFIED_DEPENDENCIES.size()
        })
    }

    String minifyDependecyName(final String input) {
        return input.grep {
            final char c = it as char
            Character.isUpperCase(c) || !Character.isLetter(c)
        }.join()
    }

    Set<String> getPreConditionsForJob(ctrlm) {
        return getTagsTextForJob(ctrlm, 'INCOND')
    }

    Set<String> getPostConditionsForJob(ctrlm) {
        return getTagsTextForJob(ctrlm, 'OUTCOND')
    }

    Set<String> getTagsTextForJob(ctrlm, tagName) {
        return ctrlm.'**'.grep { it -> it.name() == tagName }
                .collect { it['@NAME'].text() }
        .collect { minifyDependecyName(it) }
        .collect { numerifyDependecies(it) }
    }

    Map<String, Set<String>> preAndPostConditionsForJob(ctrlm) {
        def pre = getPreConditionsForJob(ctrlm)
        def pro = getPostConditionsForJob(ctrlm)

        return [pre : pre,
                ctrlm: ctrlm,
                post: pro - pre] // post: pro,
    }

    private final Comparator<GPathResult> comparator = { l, r ->
        final Map<String, Set<String>> left = preAndPostConditionsForJob(l);
        final Map<String, Set<String>> right = preAndPostConditionsForJob(r);

        // this fellas do not depend on any body, they are equal among them
        if (!left.pre && !right.pre) {
            return 0;
        }

        // si alguna dependencia del derecho `right.pre`
        // esta presente en las post del izquierdo ´left.post´
        if (right['pre'].grep(left['post'])) {
            return -1; // left es mayor que right
        }

        if (left['pre'].grep(right['post'])) {
            return 1; // left es mayor que right
        }

        return 0;
    }


}


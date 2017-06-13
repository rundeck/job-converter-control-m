package org.rundeck.controlm.jobs

import org.junit.Assert
import org.junit.Test
import org.rundeck.controlm.jobs.parsers.CountryParser

class CountryParser_UT {

    @Test
    void test_parsing() {
        final CountryParser countryParser = new CountryParser()

        Assert.assertEquals(["CL"], countryParser.parse("this text contains CL"))
        Assert.assertEquals(["CL", "AR"], countryParser.parse("CL AR"))
        Assert.assertEquals([], countryParser.parse("there is nothing in here"))
        Assert.assertEquals(["CA", "CA"], countryParser.parse("_CA- _CA_"))
    }

}

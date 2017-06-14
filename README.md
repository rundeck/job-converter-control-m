## About the converter

This converter uses Control-M 6.3 or 7 export files to generate both resources and project RUNDECK import files, in order to automate the migration process from Control-M to RUNDECK


## Generate RUNDECK resources file

```gradle resources -Pinput="./example/job_that_runs_last_monday_of_every_month.xml,./example/job_that_runs_last_thursday_of_every_month.xml"```

will produce a folder "YYYY_MM_dd_HH_mm" with a resources.xml file

_several input files can be processed separated by comma_

## Generate RUNDECK project file

```gradle jobs -Pinput="./example/job_that_runs_last_monday_of_every_month.xml,./example/job_that_runs_last_thursday_of_every_month.xml"```

_several input files can be processed separated by comma_

will produce a folder "YYYY_MM_dd_HH_mm" with the RUNDECK import xml files
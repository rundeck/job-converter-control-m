## About the converter

This converter uses Control-M 6.3 or 7 export files to generate both resources and project RUNDECK import files, in order to automate the migration process from Control-M to RUNDECK


## Generate RUNDECK resources and project files

CAUTION: the script will erase the _output_ folder before execution if it exists

```sh run.sh ./example/job_that_runs_last_monday_of_every_month.xml,./example/job_that_runs_last_thursday_of_every_month.xml```

will produce a folder "output/resources" with a resources.xml file and a folder "output/jobs" with the RUNDECK import xml files

_several input files can be processed separated by comma_

NOTE: all jobs will be generated with execution disabled for safety, you need to enable them after revision

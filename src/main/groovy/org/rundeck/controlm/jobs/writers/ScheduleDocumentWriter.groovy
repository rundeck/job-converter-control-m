package org.rundeck.controlm.jobs.writers

import org.rundeck.controlm.DocumentWriter
import org.rundeck.controlm.jobs.ControlMJob
import org.rundeck.controlm.jobs.ControlMJobs

class ScheduleDocumentWriter implements DocumentWriter {

    final Map<String, String> WEEKDAYS_TO_CRONTAB = ["0": "SUN",
                                                     "1": "MON",
                                                     "2": "TUE",
                                                     "3": "WED",
                                                     "4": "THU",
                                                     "5": "FRI",
                                                     "6": "SAT"]

    @Override
    void write(final document,
               final ControlMJob ctrlm,
               final ControlMJobs ctrlms,
               final Properties properties) {
        if (ctrlm.ignoreSchedule) {
            return;
        }

        document.scheduleEnabled(ctrlm.scheduleEnabled)

        document.schedule {
            // starting time
            String hours = getHours(ctrlm)
            String minutes = getMinutes(ctrlm)

            // interval
            final String interval = ctrlm.interval
            if (ctrlm.scheduleEnabled && interval) {
                def numbers = Integer.parseInt(interval.replaceAll(/\D/, ''), 10)
                def unit = interval.replaceAll(/\d/, '')

                if ('M' == unit && numbers == 60) {
                    unit = 'H'
                    numbers = 1
                }

                if (numbers > 0) {
                    if (unit == 'H') {
                        hours = "${hours}/${numbers}"
                    } else if (unit == 'M') {
                        minutes = "${minutes}/${numbers}"
                    }
                }
            }

            // day of month
            def days = ctrlm.days
            def weekDays = ctrlm.weekDays

            // if none defined or both "all"
            if ((!weekDays && !days) || (weekDays == 'ALL' && days == 'ALL')) {
                month(month: ctrlm.getMonthSchedule())
            // } else if (weekDays && weekDays != 'ALL') {
            } else if (weekDays) {
                weekday(day: getWeekDay(ctrlm))
                month(month: ctrlm.getMonthSchedule()) // if any
            } else {
                def daysAsNumbers = days.replaceAll(/[^\d,]/, '')
                month(day: daysAsNumbers ?: '*', month: ctrlm.getMonthSchedule()) // if any
            }
            time(hour: hours, minute: minutes, seconds: '00')
        }
    }

    private String getWeekDay(final ControlMJob ctrlm) {
        final String andOr = ctrlm.daysAndOr
        final String weekDays = ctrlm.weekDays
        final String days = ctrlm.days

        if (andOr == "OR" || !days || days == "ALL") {
            if (weekDays == "ALL") {
                return "*"
            }

            final String someDays = weekDays.replaceAll(~/(\d)/, { match ->
                WEEKDAYS_TO_CRONTAB[match[0]]
            })

            return someDays ?: '*'
        } else if (days == "L1,L2,L3,L4,L5,L6,L7") {
            // last days of month special case
            final String someDays = weekDays.replaceAll(~/(\d)/, { match ->
                WEEKDAYS_TO_CRONTAB[match[0]] + "L"
            })

            return someDays ?: '*'
        }

        throw new Exception("can't determine weekday for ctrlm: ${ctrlm}");
    }

    private String getHours(final ControlMJob ctrlm) {
        final String timeFrom = ctrlm.timeFrom
        final String timeTo = ctrlm.timeTo

        if (!timeTo) {
            return timeFrom[0..1]
        }

        return "${timeFrom[0..1]}-${timeTo[0..1]}"
    }

    private String getMinutes(final ControlMJob ctrlm) {
        final String timeFrom = ctrlm.timeFrom
        return timeFrom[2..3]
    }

    // private String getMinutes(final ControlMJob ctrlm) {
    //     final String timeFrom = ctrlm.timeFrom
    //     final String timeTo = ctrlm.timeTo

    //     if (!timeTo) {
    //         return timeFrom[2..3]
    //     }

    //     return "${timeFrom[2..3]}-${timeTo[2..3]}"
    // }

}

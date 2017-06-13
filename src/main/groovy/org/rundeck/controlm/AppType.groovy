package org.rundeck.controlm

enum AppType {
    DATABASE("DATABASE"),
    FILE_WATCH("FileWatch"),
    OS("OS"),
    FILE_TRANS("FILE_TRANS"),
    REPORT("Report")

    private static final Map<String, AppType> APPTYPES_BY_TOKEN = AppType.values()
            .collectEntries { appType -> [appType.token, appType] }

    static Optional<AppType> getByToken(final String token) {
        Optional.ofNullable(token).flatMap({ it ->
            Optional.ofNullable(APPTYPES_BY_TOKEN[it])
        })
    }

    private final String token;

    AppType(final String token) {
        this.token = token
    }

    String getToken() {
        return token
    }

}

package org.rundeck.api;

public enum RundeckAPI {
    TWO(2),
    ELEVEN(11),
    FOURTEEN(14);

    /** Version of the API supported */
    private final int apiVersion;

    /** End-point of the API */
    private final String apiEndpoint;

    RundeckAPI(final int apiVersion) {
        this.apiVersion = apiVersion;
        this.apiEndpoint = "/api/" + apiVersion;
    }

    public int getVersion() {
        return apiVersion;
    }

    public String getEndpoint() {
        return apiEndpoint;
    }

}

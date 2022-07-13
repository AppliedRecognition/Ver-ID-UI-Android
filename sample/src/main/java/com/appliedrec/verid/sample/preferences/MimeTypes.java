package com.appliedrec.verid.sample.preferences;

public enum MimeTypes {

    REGISTRATION("application/x-verid-registration");

    private final String type;

    MimeTypes(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}

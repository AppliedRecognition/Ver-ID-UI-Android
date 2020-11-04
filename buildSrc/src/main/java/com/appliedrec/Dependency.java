package com.appliedrec;

public class Dependency {

    public final String groupId;
    public final String artifactId;
    public final String version;

    public Dependency(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String toString() {
        return groupId+":"+artifactId+":"+version;
    }
}
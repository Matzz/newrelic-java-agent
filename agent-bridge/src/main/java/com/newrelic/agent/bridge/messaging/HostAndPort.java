package com.newrelic.agent.bridge.messaging;

public class HostAndPort {
    private final String hostName;
    private final Integer port;

    public static HostAndPort empty() {
        return new HostAndPort(null, null);
    }

    public HostAndPort(String host, Integer port) {
        this.hostName = host;
        this.port = port;
    }

    public String getHostName() {
        return hostName;
    }

    public Integer getPort() {
        return port;
    }
}
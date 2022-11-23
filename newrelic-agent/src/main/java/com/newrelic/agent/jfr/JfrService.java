/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.jfr;

import com.google.common.annotations.VisibleForTesting;
import com.newrelic.agent.Agent;
import com.newrelic.agent.MetricNames;
import com.newrelic.agent.ThreadService;
import com.newrelic.agent.commands.StopJfrCommand;
import com.newrelic.agent.commands.StartJfrCommand;
import com.newrelic.agent.config.AgentConfig;
import com.newrelic.agent.config.JfrConfig;
import com.newrelic.agent.service.AbstractService;
import com.newrelic.agent.service.ServiceFactory;
import com.newrelic.agent.service.Toggleable;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.jfr.ThreadNameNormalizer;
import com.newrelic.jfr.daemon.*;
import com.newrelic.telemetry.Attributes;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import static com.newrelic.jfr.daemon.AttributeNames.ENTITY_GUID;
import static com.newrelic.jfr.daemon.SetupUtils.buildCommonAttributes;
import static com.newrelic.jfr.daemon.SetupUtils.buildUploader;

public class JfrService extends AbstractService implements Toggleable {

    private final JfrConfig jfrConfig;
    private final AgentConfig defaultAgentConfig;
    private JfrController jfrController;

    public JfrService(JfrConfig jfrConfig, AgentConfig defaultAgentConfig) {
        super(JfrService.class.getSimpleName());
        this.jfrConfig = jfrConfig;
        this.defaultAgentConfig = defaultAgentConfig;
    }

    @Override
    protected void doStart() {
        addJfrServiceCommands();

        if (isEnabled()) {
            toggleOn();
        }
    }

    /**
     * Toggle the service on.
     */
    @Override
    public void toggleOn() {
        if (coreApisExist() && isEnabled()) {
            Agent.LOG.log(Level.INFO, "Attaching New Relic JFR Monitor");

            NewRelic.getAgent().getMetricAggregator().incrementCounter(MetricNames.SUPPORTABILITY_JFR_SERVICE_STARTED_SUCCESS);

            try {
                final DaemonConfig daemonConfig = buildDaemonConfig();
                final Attributes commonAttrs = buildCommonAttributes(daemonConfig);
                final String entityGuid = ServiceFactory.getRPMService().getEntityGuid();
                Agent.LOG.log(Level.INFO, "JFR Monitor obtained entity guid from agent: " + entityGuid);
                commonAttrs.put(ENTITY_GUID, entityGuid);

                final JFRUploader uploader = buildUploader(daemonConfig);
                String pattern = defaultAgentConfig.getValue(ThreadService.NAME_PATTERN_CFG_KEY, ThreadNameNormalizer.DEFAULT_PATTERN);
                uploader.readyToSend(new EventConverter(commonAttrs, pattern));
                jfrController = SetupUtils.buildJfrController(daemonConfig, uploader);

                ExecutorService jfrMonitorService = Executors.newSingleThreadExecutor();
                jfrMonitorService.submit(
                        () -> {
                            try {
                                startJfrLoop();
                            } catch (JfrRecorderException e) {
                                Agent.LOG.log(Level.INFO, "Error in JFR Monitor, shutting down", e);
                                toggleOff();
                            }
                        });

                Agent.LOG.log(Level.INFO, "JFR Monitor started");
            } catch (Throwable t) {
                Agent.LOG.log(Level.INFO, "Unable to attach JFR Monitor", t);
            }
        } else {
            Agent.LOG.log(Level.INFO, "A request was made to start the JFR Monitor but the enabled flag is false or " +
                    "necessary APIs do not exist");
        }
    }

    /**
     * Toggle the service off
     */
    @Override
    public void toggleOff() {
        if (!isEnabled()) {
            doStop();
            Agent.LOG.log(Level.INFO, "JFR Monitor stopped");
        }
    }

    // Using Mockito spy, verify execution of this method as start of jfr.
    void startJfrLoop() throws JfrRecorderException {
        jfrController.loop();
    }

    @Override
    public final boolean isEnabled() {
        final boolean enabled = jfrConfig.isEnabled();
        Agent.LOG.log(Level.INFO, "New Relic JFR Monitor enabled flag: {0}", enabled);
        return enabled;
    }

    @Override
    protected void doStop() {
        if (jfrController != null) {
            jfrController.shutdown();
        }
    }

    private void addJfrServiceCommands() {
        Agent.LOG.log(Level.FINEST, "Adding JFR specific commands to CommandParser");
        ServiceFactory.getCommandParser().addCommands(
                new StartJfrCommand(this, jfrConfig), new StopJfrCommand(this, jfrConfig));
    }

    @VisibleForTesting
    boolean coreApisExist() {
        try {
            Class.forName("jdk.jfr.Recording");
            Class.forName("jdk.jfr.FlightRecorder");
        } catch (ClassNotFoundException __) {
            Agent.LOG.log(Level.WARNING, "Not starting JFR Service. Core JFR APIs do not exist in this JVM.");
            return false;
        }
        return true;
    }

    @VisibleForTesting
    DaemonConfig buildDaemonConfig() {
        DaemonConfig.Builder builder = DaemonConfig.builder()
                .daemonVersion(VersionFinder.getVersion())
                .useLicenseKey(jfrConfig.useLicenseKey())
                .apiKey(defaultAgentConfig.getLicenseKey())
                .monitoredAppName(defaultAgentConfig.getApplicationName())
                .auditLogging(jfrConfig.auditLoggingEnabled())
                .metricsUri(URI.create(defaultAgentConfig.getMetricIngestUri()))
                .eventsUri(URI.create(defaultAgentConfig.getEventIngestUri()))
                .proxyHost(defaultAgentConfig.getProxyHost())
                .proxyScheme(defaultAgentConfig.getProxyScheme())
                .proxyPort(defaultAgentConfig.getProxyPort())
                .proxyUser(defaultAgentConfig.getProxyUser())
                .proxyPassword(defaultAgentConfig.getProxyPassword());
        return builder.build();
    }
}

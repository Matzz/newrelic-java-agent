/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.bridge;

import com.newrelic.api.agent.Config;
import com.newrelic.api.agent.ErrorApi;
import com.newrelic.api.agent.Insights;
import com.newrelic.api.agent.Logger;
import com.newrelic.api.agent.Logs;
import com.newrelic.api.agent.MetricAggregator;
import com.newrelic.api.agent.TraceMetadata;

import java.util.Collections;
import java.util.Map;

class NoOpAgent implements Agent {

    static final Agent INSTANCE = new NoOpAgent();

    private NoOpAgent() {
    }

    @Override
    public Logger getLogger() {
        return com.newrelic.api.agent.Agent.noop().getLogger();
    }

    @Override
    public Config getConfig() {
        return com.newrelic.api.agent.Agent.noop().getConfig();
    }

    @Override
    public TracedMethod getTracedMethod() {
        return NoOpTracedMethod.INSTANCE;
    }

    @Override
    public Transaction getTransaction() {
        return NoOpTransaction.INSTANCE;
    }

    @Override
    public Transaction getTransaction(boolean createIfNotExists) {
        return NoOpTransaction.INSTANCE;
    }

    @Override
    public Transaction getWeakRefTransaction(boolean createIfNotExists) {
        return NoOpTransaction.INSTANCE;
    }

    @Override
    public MetricAggregator getMetricAggregator() {
        return com.newrelic.api.agent.Agent.noop().getMetricAggregator();
    }

    @Override
    public Insights getInsights() {
        return com.newrelic.api.agent.Agent.noop().getInsights();
    }

    @Override
    public ErrorApi getErrorApi() {
        return com.newrelic.api.agent.Agent.noop().getErrorApi();
    }

    @Override
    public Logs getLogSender() {
        return NoOpLogs.INSTANCE;
    }

    @Override
    public boolean startAsyncActivity(Object activityContext) {
        return false;
    }

    @Override
    public boolean ignoreIfUnstartedAsyncContext(Object activityContext) {
        return false;
    }

    @Override
    public TraceMetadata getTraceMetadata() {
        return NoOpTraceMetadata.INSTANCE;
    }

    @Override
    public Map<String, String> getLinkingMetadata() {
        return Collections.emptyMap();
    }
}

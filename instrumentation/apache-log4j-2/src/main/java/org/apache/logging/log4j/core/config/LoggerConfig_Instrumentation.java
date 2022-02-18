package org.apache.logging.log4j.core.config;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.NewField;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.WeaveAllConstructors;
import com.newrelic.api.agent.weaver.Weaver;
import org.apache.logging.log4j.core.LogEvent;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.nr.agent.instrumentation.log4j2.AgentUtil.isApplicationLoggingEnabled;
import static com.nr.agent.instrumentation.log4j2.AgentUtil.isApplicationLoggingForwardingEnabled;
import static com.nr.agent.instrumentation.log4j2.AgentUtil.isApplicationLoggingMetricsEnabled;
import static com.nr.agent.instrumentation.log4j2.AgentUtil.recordNewRelicLogEvent;

@Weave(originalName = "org.apache.logging.log4j.core.config.LoggerConfig", type = MatchType.ExactClass)
public class LoggerConfig_Instrumentation {
    @NewField
    public static AtomicBoolean instrumented = new AtomicBoolean(false);

    @WeaveAllConstructors
    public LoggerConfig_Instrumentation() {
        // Generate the instrumentation module supportability metric only once
        if (!instrumented.getAndSet(true)) {
            NewRelic.incrementCounter("Supportability/Logging/enabled/Java/Log4j2");
        }
    }

    protected void callAppenders(LogEvent event) {
        // Do nothing if application_logging.enabled: false
        if (isApplicationLoggingEnabled()) {
            if (isApplicationLoggingMetricsEnabled()) {
                // Generate log level metrics
                NewRelic.incrementCounter("Logging/lines");
                NewRelic.incrementCounter("Logging/lines/" + event.getLevel().toString());
            }

            if (isApplicationLoggingForwardingEnabled()) {
                // Record and send LogEvent to New Relic
                recordNewRelicLogEvent(event);
            }
        }
        Weaver.callOriginal();
    }
}
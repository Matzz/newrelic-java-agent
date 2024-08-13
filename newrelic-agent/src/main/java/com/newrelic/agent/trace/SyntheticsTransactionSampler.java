/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.trace;

import com.newrelic.agent.Agent;
import com.newrelic.agent.TransactionData;
import com.newrelic.agent.attributes.AttributeNames;

import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * This sampler collects transactions generated by New Relic Synthetics up to a fixed limit and transmits their traces
 * at harvest time. This class does not really "sample: it behaves like a fixed-size accumulator. In order to guarantee
 * this behavior, however, this sampler must run at the head of the sampler search conducted by the
 * {@link TransactionTraceService}. Otherwise, the synthetic transaction may be "stolen" by a sampler with some
 * heuristic replacement behavior and then discarded before the following harvest.
 */
public class SyntheticsTransactionSampler implements ITransactionSampler {

    /*
     * This class uses lockless concurrency to maintain a bounded buffer. The Synthetics spec says we need to retain at
     * least 20 Synthetics transactions per harvest, and I choose to interpret it as allowing the possibility of a few
     * more.
     * 
     * One issue with Java's lockless ConcurrentLinkedQueue is that size() is a linear-time operation.
     * 
     * So we maintain a volatile counter of the number of pending transactions (really TransactionData objects).
     * Incoming samples check the counter and return if it's greater than the limit. Otherwise, they increment the
     * counter and enqueue their item. The act of (increment, enqueue) is not synchronized, but is nonblocking so will
     * eventually complete. Multiple threads may read the counter just below the limit and choose to enqueue, leaving
     * more than the limit number of items on the queue.
     * 
     * At harvest time, the harvester pushes a marker item on the end of the queue and the dequeues items from the head
     * until it consumes the marker. For each item it dequeues, it decrements the counter. It actually collects the
     * total number internally and then decrements the counter atomically by the entire amount so that the value won't
     * hover around 20 if incoming transactions are racing with the harvest loop; hovering around 20 is exactly what
     * this deterministic collector is supposed to prevent, because it would result in lost traces intertwingled with
     * held traces.
     * 
     * Since there is no locking around either the (increment, enqueue) or the (dequeue, decrement) sequences, the
     * counter may not correctly indicate the actual number of items on the queue at a given point in time. But since
     * there are no blocking operations, the counter is "eventually consistent" with the sum of enqueues and dequeues.
     * 
     * This counter behavior is perfectly adequate to prevent the sampler from consuming too much storage in the face of
     * a flood of transaction completions. But the counter value must never be used to control the number of operations
     * performed anywhere, such as in the harvester. The marker algorithm allows us to avoid using the counter in this
     * erroneous way.
     */
    private final ConcurrentLinkedQueue<TransactionData> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    static final int MAX_SYNTHETIC_TRANSACTION_PER_HARVEST = 20;
    private static final TransactionData queueMarker = new TransactionData(null, 0);

    @Override
    public boolean noticeTransaction(TransactionData td) {
        if (td.isSyntheticTransaction()) {
            if (pendingCount.get() < MAX_SYNTHETIC_TRANSACTION_PER_HARVEST) {
                // TODO this probably needs to be done in harvest method below, reset transaction trace attribute on current expensive transaction
//                markAsTransactionTraceCandidate(current, false);
                // set transaction trace attribute on new expensive transaction
                markAsTransactionTraceCandidate(td, true);

                // Here is the window described in the first paragraph, above
                pendingCount.incrementAndGet();
                pending.add(td);
                String msg = MessageFormat.format("Sampled Synthetics Transaction: {0}", td);
                Agent.LOG.finest(msg);
                return true;
            }
            Agent.LOG.log(Level.FINER, "Dropped Synthetic TT for app {0}", td.getApplicationName());
            // This Synthetics trace may be collected by samplers that run after us.
        }

        return false;
    }

    private void markAsTransactionTraceCandidate(TransactionData td, boolean isTransactionTrace) {
        if (td != null) {
            Map<String, Object> intrinsicAttributes = td.getIntrinsicAttributes();
            if (intrinsicAttributes != null) {
                intrinsicAttributes.put(AttributeNames.TRANSACTION_TRACE, isTransactionTrace);

                Float priority = (Float) intrinsicAttributes.get(AttributeNames.PRIORITY);
                Float ttPriority = priority + 5;
                intrinsicAttributes.put(AttributeNames.PRIORITY, ttPriority);

                td.getTransaction().setPriorityIfNotNull(ttPriority);
            }
        }
    }

    @Override
    public List<TransactionTrace> harvest(String appName) {
        List<TransactionTrace> result = new LinkedList<>();
        if (appName == null) {
            return result;
        }

        pending.add(queueMarker); // we neither increment nor decrement the counter for the marker item.
        int removedCount = 0;

        TransactionData queued;
        while ((queued = pending.poll()) != queueMarker) {
            if (appName.equals(queued.getApplicationName())) {
                TransactionTrace tt = TransactionTrace.getTransactionTrace(queued);
                tt.setSyntheticsResourceId(queued.getSyntheticsResourceId());
                removedCount++;
                // TODO here is where we actually choose traces
                result.add(tt);
            } else {
                // TODO reset transaction trace attribute on current expensive transaction
//                markAsTransactionTraceCandidate(current, false);
                // Add it back to be reconsidered on the next call.
                pending.add(queued);
            }
        }
        pendingCount.addAndGet(-removedCount);
        return result;
    }

    @Override
    public void stop() {
        // As of Agent 3.12.0, this only gets called on some samplers.
        // This sampler is not one of them.
    }

    // Used by tests only for an assertion after shutting down the sampler.
    int getPendingCount() {
        return pendingCount.get();
    }
}

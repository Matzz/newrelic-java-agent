/*
 *
 *  * Copyright 2024 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package llm.models.amazon.titan;

import com.newrelic.agent.introspec.Event;
import com.newrelic.agent.introspec.InstrumentationTestConfig;
import com.newrelic.agent.introspec.InstrumentationTestRunner;
import com.newrelic.agent.introspec.Introspector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeResponseMetadata;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static llm.events.LlmEvent.LLM_CHAT_COMPLETION_MESSAGE;
import static llm.events.LlmEvent.LLM_CHAT_COMPLETION_SUMMARY;
import static llm.events.LlmEvent.LLM_EMBEDDING;
import static llm.models.TestUtil.assertErrorEvent;
import static llm.models.TestUtil.assertLlmChatCompletionMessageAttributes;
import static llm.models.TestUtil.assertLlmChatCompletionSummaryAttributes;
import static llm.models.TestUtil.assertLlmEmbeddingAttributes;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(InstrumentationTestRunner.class)
@InstrumentationTestConfig(includePrefixes = { "software.amazon.awssdk.services.bedrockruntime" }, configName = "llm_enabled.yml")
public class TitanModelInvocationTest {

    private final Introspector introspector = InstrumentationTestRunner.getIntrospector();

    // Embedding
    private final String embeddingModelId = "amazon.titan-embed-text-v1";
    private final String embeddingRequestBody = "{\"inputText\":\"What is the color of the sky?\"}";
    private final String embeddingResponseBody = "{\"embedding\":[0.328125,0.44335938],\"inputTextTokenCount\":8}";
    private final String embeddingRequestInput = "What is the color of the sky?";

    // Completion
    private final String completionModelId = "amazon.titan-text-lite-v1";
    private final String completionRequestBody = "{\"inputText\":\"What is the color of the sky?\",\"textGenerationConfig\":{\"maxTokenCount\":1000,\"stopSequences\":[\"User:\"],\"temperature\":0.5,\"topP\":0.9}}";
    private final String completionResponseBody = "{\"inputTextTokenCount\":8,\"results\":[{\"tokenCount\":9,\"outputText\":\"\\nThe color of the sky is blue.\",\"completionReason\":\"FINISH\"}]}";
    private final String completionRequestInput = "What is the color of the sky?";
    private final String completionResponseContent = "\nThe color of the sky is blue.";
    private final String finishReason = "FINISH";

    @Before
    public void before() {
        introspector.clear();
    }

    @Test
    public void testEmbedding() {
        boolean isError = false;

        TitanModelInvocation titanModelInvocation = mockTitanModelInvocation(embeddingModelId, embeddingRequestBody, embeddingResponseBody, isError);
        titanModelInvocation.recordLlmEvents(System.currentTimeMillis());

        Collection<Event> llmEmbeddingEvents = introspector.getCustomEvents(LLM_EMBEDDING);
        assertEquals(1, llmEmbeddingEvents.size());
        Iterator<Event> llmEmbeddingEventIterator = llmEmbeddingEvents.iterator();
        Event llmEmbeddingEvent = llmEmbeddingEventIterator.next();

        assertLlmEmbeddingAttributes(llmEmbeddingEvent, embeddingModelId, embeddingRequestInput);

        assertErrorEvent(isError, introspector.getErrorEvents());
    }

    @Test
    public void testCompletion() {
        boolean isError = false;

        TitanModelInvocation titanModelInvocation = mockTitanModelInvocation(completionModelId, completionRequestBody, completionResponseBody, isError);
        titanModelInvocation.recordLlmEvents(System.currentTimeMillis());

        Collection<Event> llmChatCompletionMessageEvents = introspector.getCustomEvents(LLM_CHAT_COMPLETION_MESSAGE);
        assertEquals(2, llmChatCompletionMessageEvents.size());
        Iterator<Event> llmChatCompletionMessageEventIterator = llmChatCompletionMessageEvents.iterator();
        Event llmChatCompletionMessageEventOne = llmChatCompletionMessageEventIterator.next();

        assertLlmChatCompletionMessageAttributes(llmChatCompletionMessageEventOne, completionModelId, completionRequestInput, completionResponseContent, false);

        Event llmChatCompletionMessageEventTwo = llmChatCompletionMessageEventIterator.next();

        assertLlmChatCompletionMessageAttributes(llmChatCompletionMessageEventTwo, completionModelId, completionRequestInput, completionResponseContent, true);

        Collection<Event> llmChatCompletionSummaryEvents = introspector.getCustomEvents(LLM_CHAT_COMPLETION_SUMMARY);
        assertEquals(1, llmChatCompletionSummaryEvents.size());
        Iterator<Event> llmChatCompletionSummaryEventIterator = llmChatCompletionSummaryEvents.iterator();
        Event llmChatCompletionSummaryEvent = llmChatCompletionSummaryEventIterator.next();

        assertLlmChatCompletionSummaryAttributes(llmChatCompletionSummaryEvent, completionModelId, finishReason);

        assertErrorEvent(isError, introspector.getErrorEvents());
    }

    @Test
    public void testEmbeddingError() {
        boolean isError = true;

        TitanModelInvocation titanModelInvocation = mockTitanModelInvocation(embeddingModelId, embeddingRequestBody, embeddingResponseBody, isError);
        titanModelInvocation.recordLlmEvents(System.currentTimeMillis());

        Collection<Event> llmEmbeddingEvents = introspector.getCustomEvents(LLM_EMBEDDING);
        assertEquals(1, llmEmbeddingEvents.size());
        Iterator<Event> llmEmbeddingEventIterator = llmEmbeddingEvents.iterator();
        Event llmEmbeddingEvent = llmEmbeddingEventIterator.next();

        assertLlmEmbeddingAttributes(llmEmbeddingEvent, embeddingModelId, embeddingRequestInput);

        assertErrorEvent(isError, introspector.getErrorEvents());
    }

    @Test
    public void testCompletionError() {
        boolean isError = true;

        TitanModelInvocation titanModelInvocation = mockTitanModelInvocation(completionModelId, completionRequestBody, completionResponseBody, isError);
        titanModelInvocation.recordLlmEvents(System.currentTimeMillis());

        Collection<Event> llmChatCompletionMessageEvents = introspector.getCustomEvents(LLM_CHAT_COMPLETION_MESSAGE);
        assertEquals(2, llmChatCompletionMessageEvents.size());
        Iterator<Event> llmChatCompletionMessageEventIterator = llmChatCompletionMessageEvents.iterator();
        Event llmChatCompletionMessageEventOne = llmChatCompletionMessageEventIterator.next();

        assertLlmChatCompletionMessageAttributes(llmChatCompletionMessageEventOne, completionModelId, completionRequestInput, completionResponseContent, false);

        Event llmChatCompletionMessageEventTwo = llmChatCompletionMessageEventIterator.next();

        assertLlmChatCompletionMessageAttributes(llmChatCompletionMessageEventTwo, completionModelId, completionRequestInput, completionResponseContent, true);

        Collection<Event> llmChatCompletionSummaryEvents = introspector.getCustomEvents(LLM_CHAT_COMPLETION_SUMMARY);
        assertEquals(1, llmChatCompletionSummaryEvents.size());
        Iterator<Event> llmChatCompletionSummaryEventIterator = llmChatCompletionSummaryEvents.iterator();
        Event llmChatCompletionSummaryEvent = llmChatCompletionSummaryEventIterator.next();

        assertLlmChatCompletionSummaryAttributes(llmChatCompletionSummaryEvent, completionModelId, finishReason);

        assertErrorEvent(isError, introspector.getErrorEvents());
    }

    private TitanModelInvocation mockTitanModelInvocation(String modelId, String requestBody, String responseBody, boolean isError) {
        // Given
        Map<String, String> linkingMetadata = new HashMap<>();
        linkingMetadata.put("span.id", "span-id-123");
        linkingMetadata.put("trace.id", "trace-id-xyz");

        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("llm.conversation_id", "conversation-id-value");
        userAttributes.put("llm.testPrefix", "testPrefix");
        userAttributes.put("test", "test");

        // Mock out ModelRequest
        InvokeModelRequest mockInvokeModelRequest = mock(InvokeModelRequest.class);
        SdkBytes mockRequestSdkBytes = mock(SdkBytes.class);
        when(mockInvokeModelRequest.body()).thenReturn(mockRequestSdkBytes);
        when(mockRequestSdkBytes.asUtf8String()).thenReturn(requestBody);
        when(mockInvokeModelRequest.modelId()).thenReturn(modelId);

        // Mock out ModelResponse
        InvokeModelResponse mockInvokeModelResponse = mock(InvokeModelResponse.class);
        SdkBytes mockResponseSdkBytes = mock(SdkBytes.class);
        when(mockInvokeModelResponse.body()).thenReturn(mockResponseSdkBytes);
        when(mockResponseSdkBytes.asUtf8String()).thenReturn(responseBody);

        SdkHttpResponse mockSdkHttpResponse = mock(SdkHttpResponse.class);
        when(mockInvokeModelResponse.sdkHttpResponse()).thenReturn(mockSdkHttpResponse);

        if (isError) {
            when(mockSdkHttpResponse.statusCode()).thenReturn(400);
            when(mockSdkHttpResponse.statusText()).thenReturn(Optional.of("BAD_REQUEST"));
            when(mockSdkHttpResponse.isSuccessful()).thenReturn(false);
        } else {
            when(mockSdkHttpResponse.statusCode()).thenReturn(200);
            when(mockSdkHttpResponse.statusText()).thenReturn(Optional.of("OK"));
            when(mockSdkHttpResponse.isSuccessful()).thenReturn(true);
        }

        BedrockRuntimeResponseMetadata mockBedrockRuntimeResponseMetadata = mock(BedrockRuntimeResponseMetadata.class);
        when(mockInvokeModelResponse.responseMetadata()).thenReturn(mockBedrockRuntimeResponseMetadata);
        when(mockBedrockRuntimeResponseMetadata.requestId()).thenReturn("90a22e92-db1d-4474-97a9-28b143846301");

        // Instantiate ModelInvocation
        return new TitanModelInvocation(linkingMetadata, userAttributes, mockInvokeModelRequest,
                mockInvokeModelResponse);
    }
}

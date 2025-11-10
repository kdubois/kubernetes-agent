package org.csanchez.rollout.agents;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import org.csanchez.rollout.k8sagent.a2a.A2AAgentExecutor;
import org.csanchez.rollout.k8sagent.agents.KubernetesAgent;
import org.csanchez.rollout.k8sagent.service.AgentResponseFormatter;
import org.csanchez.rollout.k8sagent.service.AgentResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for A2AAgentExecutor memory ID extraction logic.
 */
@ExtendWith(MockitoExtension.class)
class A2AAgentExecutorTest {

    @Mock
    private KubernetesAgent kubernetesAgent;
    
    @Mock
    private AgentResponseParser responseParser;
    
    @Mock
    private AgentResponseFormatter responseFormatter;

    private A2AAgentExecutor a2aAgentExecutor;
    private AgentExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        a2aAgentExecutor = new A2AAgentExecutor();
        
        // Inject mocks using reflection
        Field kubernetesAgentField = A2AAgentExecutor.class.getDeclaredField("kubernetesAgent");
        kubernetesAgentField.setAccessible(true);
        kubernetesAgentField.set(a2aAgentExecutor, kubernetesAgent);
        
        Field responseParserField = A2AAgentExecutor.class.getDeclaredField("responseParser");
        responseParserField.setAccessible(true);
        responseParserField.set(a2aAgentExecutor, responseParser);
        
        Field responseFormatterField = A2AAgentExecutor.class.getDeclaredField("responseFormatter");
        responseFormatterField.setAccessible(true);
        responseFormatterField.set(a2aAgentExecutor, responseFormatter);
        
        executor = a2aAgentExecutor.agentExecutor();
    }

    @Test
    void testMemoryIdFromMetadata() throws Exception {
        // Given: A message with memoryId in metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memoryId", "test-memory-123");
        
        Message message = mock(Message.class);
        when(message.getMetadata()).thenReturn(metadata);
        when(message.getParts()).thenReturn(null);
        
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-456");
        
        RequestContext context = mock(RequestContext.class);
        when(context.getMessage()).thenReturn(message);
        when(context.getTask()).thenReturn(task);
        
        when(kubernetesAgent.chat(anyString(), anyString())).thenReturn("test response");
        
        // When: Execute is called
        executor.execute(context, null);
        
        // Then: memoryId from metadata should be used
        ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubernetesAgent).chat(memoryIdCaptor.capture(), anyString());
        assertEquals("test-memory-123", memoryIdCaptor.getValue());
    }

    @Test
    void testUserIdFromMetadata() throws Exception {
        // Given: A message with userId in metadata (no memoryId)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", "user-789");
        
        Message message = mock(Message.class);
        when(message.getMetadata()).thenReturn(metadata);
        when(message.getParts()).thenReturn(null);
        
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-456");
        
        RequestContext context = mock(RequestContext.class);
        when(context.getMessage()).thenReturn(message);
        when(context.getTask()).thenReturn(task);
        
        when(kubernetesAgent.chat(anyString(), anyString())).thenReturn("test response");
        
        // When: Execute is called
        executor.execute(context, null);
        
        // Then: userId from metadata should be used as memoryId
        ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubernetesAgent).chat(memoryIdCaptor.capture(), anyString());
        assertEquals("user-789", memoryIdCaptor.getValue());
    }

    @Test
    void testSessionIdFromMetadata() throws Exception {
        // Given: A message with sessionId in metadata (no memoryId or userId)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", "session-abc");
        
        Message message = mock(Message.class);
        when(message.getMetadata()).thenReturn(metadata);
        when(message.getParts()).thenReturn(null);
        
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-456");
        
        RequestContext context = mock(RequestContext.class);
        when(context.getMessage()).thenReturn(message);
        when(context.getTask()).thenReturn(task);
        
        when(kubernetesAgent.chat(anyString(), anyString())).thenReturn("test response");
        
        // When: Execute is called
        executor.execute(context, null);
        
        // Then: sessionId from metadata should be used as memoryId
        ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubernetesAgent).chat(memoryIdCaptor.capture(), anyString());
        assertEquals("session-abc", memoryIdCaptor.getValue());
    }

    @Test
    void testFallbackToTaskId() throws Exception {
        // Given: A message with no metadata identifiers
        Message message = mock(Message.class);
        when(message.getMetadata()).thenReturn(new HashMap<>());
        when(message.getParts()).thenReturn(null);
        
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-fallback-999");
        
        RequestContext context = mock(RequestContext.class);
        when(context.getMessage()).thenReturn(message);
        when(context.getTask()).thenReturn(task);
        
        when(kubernetesAgent.chat(anyString(), anyString())).thenReturn("test response");
        
        // When: Execute is called
        executor.execute(context, null);
        
        // Then: task ID should be used as fallback
        ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubernetesAgent).chat(memoryIdCaptor.capture(), anyString());
        assertEquals("task-fallback-999", memoryIdCaptor.getValue());
    }

    @Test
    void testFallbackToDefault() throws Exception {
        // Given: A message with no metadata and no task
        Message message = mock(Message.class);
        when(message.getMetadata()).thenReturn(new HashMap<>());
        when(message.getParts()).thenReturn(null);
        
        RequestContext context = mock(RequestContext.class);
        when(context.getMessage()).thenReturn(message);
        when(context.getTask()).thenReturn(null);
        
        when(kubernetesAgent.chat(anyString(), anyString())).thenReturn("test response");
        
        // When: Execute is called
        executor.execute(context, null);
        
        // Then: "default" should be used as last resort
        ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubernetesAgent).chat(memoryIdCaptor.capture(), anyString());
        assertEquals("default", memoryIdCaptor.getValue());
    }

    @Test
    void testMemoryIdPriorityOverUserId() throws Exception {
        // Given: A message with both memoryId and userId in metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memoryId", "explicit-memory-id");
        metadata.put("userId", "user-should-not-be-used");
        
        Message message = mock(Message.class);
        when(message.getMetadata()).thenReturn(metadata);
        when(message.getParts()).thenReturn(null);
        
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-456");
        
        RequestContext context = mock(RequestContext.class);
        when(context.getMessage()).thenReturn(message);
        when(context.getTask()).thenReturn(task);
        
        when(kubernetesAgent.chat(anyString(), anyString())).thenReturn("test response");
        
        // When: Execute is called
        executor.execute(context, null);
        
        // Then: memoryId should take priority over userId
        ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubernetesAgent).chat(memoryIdCaptor.capture(), anyString());
        assertEquals("explicit-memory-id", memoryIdCaptor.getValue());
    }
}

// Made with Bob

package com.example.appointment;

import com.cajunsystems.ActorContext;
import com.cajunsystems.Pid;
import com.cajunsystems.handler.StatefulHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

public class AppointmentAgentHandler
        implements StatefulHandler<AppointmentState, AppointmentMessage> {

    private final HttpClient httpClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public AppointmentAgentHandler() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AppointmentState receive(
            AppointmentMessage message,
            AppointmentState state,
            ActorContext context
    ) {
        return switch (message) {
            case UserMessage um -> handleUserMessage(um, state, context);
            case GetAvailableSlots gas -> handleGetSlots(gas, state, context);
            case BookAppointment ba -> handleBooking(ba, state, context);
            case LLMResponse lr -> state.addMessage("assistant", lr.content());
        };
    }

    private AppointmentState handleUserMessage(
            UserMessage msg,
            AppointmentState state,
            ActorContext context
    ) {
        // Add user message to history
        AppointmentState newState = state.addMessage("user", msg.text());

        // Prepare system prompt with current availability
        String systemPrompt = buildSystemPrompt(state);

        // Call LLM asynchronously
        callLLM(systemPrompt, newState, context, msg.replyTo());

        return newState;
    }

    private String buildSystemPrompt(AppointmentState state) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an appointment scheduling assistant. ");
        prompt.append("Here are the available time slots:\\n");

        state.availableSlots().forEach((date, times) -> {
            prompt.append(String.format("%s: %s\\n",
                    date,
                    String.join(", ", times)));
        });

        prompt.append("\\nHelp the user find and book a suitable time slot. ");
        prompt.append("If they want to book, extract the date and time clearly.");

        return prompt.toString();
    }

    private void callLLM(
            String systemPrompt,
            AppointmentState state,
            ActorContext context,
            Pid replyTo
    ) {
        // Build request body
        String requestBody = buildOpenAIRequest(systemPrompt, state);

        // Make async HTTP call
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Send request asynchronously
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    String llmContent = parseOpenAIResponse(response.body());

                    // Send response back to user
                    context.tell(replyTo, new AgentResponse(llmContent));

                    // Update our state with assistant message
                    context.tellSelf(new LLMResponse(llmContent));
                })
                .exceptionally(ex -> {
                    context.tell(replyTo,
                            new AgentResponse("Sorry, I encountered an error: " + ex.getMessage()));
                    return null;
                });
    }

    private AppointmentState handleBooking(
            BookAppointment msg,
            AppointmentState state,
            ActorContext context
    ) {
        // Validate the booking
        List<String> slots = state.availableSlots().get(msg.date());

        if (slots != null && slots.contains(msg.time())) {
            // Book the slot
            AppointmentState newState = state.bookSlot(msg.date(), msg.time());

            context.tell(msg.replyTo(),
                    new AgentResponse(
                            String.format("Great! I've booked your appointment for %s at %s",
                                    msg.date(),
                                    msg.time())));

            return newState;
        } else {
            context.tell(msg.replyTo(),
                    new AgentResponse("Sorry, that slot is not available."));
            return state;
        }
    }

    private AppointmentState handleGetSlots(
            GetAvailableSlots msg,
            AppointmentState state,
            ActorContext context
    ) {
        StringBuilder slots = new StringBuilder("Available slots:\\n");
        state.availableSlots().forEach((date, times) -> {
            slots.append(String.format("%s: %s\\n", date, String.join(", ", times)));
        });

        context.tell(msg.replyTo(), new AgentResponse(slots.toString()));
        return state;
    }

    private String buildOpenAIRequest(String systemPrompt, AppointmentState state) {
        // Build JSON request
        String conversationHistory = state.conversationHistory().stream()
                .map(m -> String.format("{\"role\": \"%s\", \"content\": \"%s\"}",
                        m.role(), escapeJson(m.content())))
                .collect(Collectors.joining(",\n                    "));

        return String.format("""
                        {
                            "model": "gpt-4",
                            "messages": [
                                {"role": "system", "content": "%s"}%s
                            ]
                        }
                        """,
                escapeJson(systemPrompt),
                conversationHistory.isEmpty() ? "" : ",\n                    " + conversationHistory);
    }

    private String parseOpenAIResponse(String responseBody) {
        // Parse JSON response
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0)
                    .path("message").path("content").asText();
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}


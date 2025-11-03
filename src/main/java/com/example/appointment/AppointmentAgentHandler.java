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
            case GetBookedAppointments gba -> handleGetBookings(gba, state, context);
            case BookAppointment ba -> handleBooking(ba, state, context);
            case LLMResponse lr -> state.addMessage("assistant", lr.content());
            case CancelAppointment ca -> handleCancel(ca, state, context);
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
        prompt.append("Here are the available time slots:\n");

        state.availableSlots().forEach((date, times) -> {
            prompt.append(String.format("%s: %s\n",
                    date,
                    String.join(", ", times)));
        });

        prompt.append("\nHelp the user find and book a suitable time slot. ");
        prompt.append("When the user confirms they want to book a specific date and time, ");
        prompt.append("respond with EXACTLY this format on a new line:\n");
        prompt.append("BOOK:YYYY-MM-DD:HH:MM\n");
        prompt.append("For example: BOOK:2025-11-06:09:00\n");
        prompt.append("Then on the next line, provide your friendly confirmation message.");
        prompt.append("\nIf the user wants to cancel an appointment, respond with EXACTLY this format on a new line:\n");
        prompt.append("CANCEL:YYYY-MM-DD:HH:MM\n");
        prompt.append("For example: CANCEL:2025-11-06:09:00\n");
        prompt.append("Then on the next line, provide your friendly cancellation confirmation message.\n");

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

                    // Check if LLM wants to book an appointment
                    String bookingCommand = extractBookingCommand(llmContent);
                    if (bookingCommand != null) {
                        // Parse the booking command: BOOK:YYYY-MM-DD:HH:MM
                        String afterBook = bookingCommand.substring(5); // Remove "BOOK:"
                        String[] parts = afterBook.split(":", 2); // Split into date and time
                        if (parts.length == 2) {
                            String date = parts[0]; // YYYY-MM-DD
                            String time = parts[1]; // HH:MM

                            // Trigger the actual booking
                            context.tellSelf(new BookAppointment(date, time, replyTo));

                            // Remove the booking command from the response (including the line)
                            llmContent = llmContent.replace(bookingCommand, "").trim();
                            // Clean up multiple newlines
                            llmContent = llmContent.replaceAll("\\n\\s*\\n", "\\n").trim();
                        }
                    }

                    // Check if LLM wants to cancel an appointment
                    String cancelCommand = extractCancelCommand(llmContent);
                    if (cancelCommand != null) {
                        // Parse the cancel command: CANCEL:YYYY-MM-DD:HH:MM
                        String afterCancel = cancelCommand.substring(7); // Remove "CANCEL:"
                        String[] parts = afterCancel.split(":", 2); // Split into date and time
                        if (parts.length == 2) {
                            String date = parts[0]; // YYYY-MM-DD
                            String time = parts[1]; // HH:MM
                            // Trigger the actual cancellation
                            context.tellSelf(new CancelAppointment(date, time, replyTo));
                            // Remove the cancel command from the response (including the line)
                            llmContent = llmContent.replace(cancelCommand, "").trim();
                            llmContent = llmContent.replaceAll("\\n\\s*\\n", "\\n").trim();
                        }
                    }

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
            // Book the slot - the confirmation message was already sent by the AI
            AppointmentState newState = state.bookSlot(msg.date(), msg.time());
            return newState;
        } else {
            // Only send error message if booking failed
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
        StringBuilder slots = new StringBuilder("Available slots:\n");
        state.availableSlots().forEach((date, times) -> {
            slots.append(String.format("%s: %s\n", date, String.join(", ", times)));
        });

        context.tell(msg.replyTo(), new AgentResponse(slots.toString()));
        return state;
    }

    private AppointmentState handleGetBookings(
            GetBookedAppointments msg,
            AppointmentState state,
            ActorContext context
    ) {
        if (state.confirmedBookings().isEmpty()) {
            context.tell(msg.replyTo(), new AgentResponse("No appointments booked yet."));
        } else {
            StringBuilder bookings = new StringBuilder("Booked appointments:\n");
            state.confirmedBookings().forEach(booking -> {
                bookings.append(String.format("  - %s at %s\n", booking.date(), booking.time()));
            });
            context.tell(msg.replyTo(), new AgentResponse(bookings.toString()));
        }
        return state;
    }

    private AppointmentState handleCancel(
            CancelAppointment msg,
            AppointmentState state,
            ActorContext context
    ) {
        if (state.hasBooking(msg.date(), msg.time())) {
            AppointmentState newState = state.removeBooking(msg.date(), msg.time());
            context.tell(msg.replyTo(), new AgentResponse("Your appointment on " + msg.date() + " at " + msg.time() + " has been cancelled."));
            return newState;
        } else {
            context.tell(msg.replyTo(), new AgentResponse("No such appointment found to cancel."));
            return state;
        }
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

    private String extractBookingCommand(String llmResponse) {
        // Look for BOOK:YYYY-MM-DD:HH:MM pattern anywhere in the response
        // Pattern: BOOK followed by date (YYYY-MM-DD) and time (HH:MM)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("BOOK:(\\d{4}-\\d{2}-\\d{2}):(\\d{2}:\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(llmResponse);

        if (matcher.find()) {
            return matcher.group(0); // Returns the full match: BOOK:2025-11-06:09:00
        }

        return null;
    }

    private String extractCancelCommand(String llmResponse) {
        // Look for CANCEL:YYYY-MM-DD:HH:MM pattern anywhere in the response
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("CANCEL:(\\d{4}-\\d{2}-\\d{2}):(\\d{2}:\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(0); // Returns the full match: CANCEL:2025-11-06:09:00
        }
        return null;
    }
}


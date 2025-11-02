# AI Agent with Cajun Actor Framework

This project demonstrates how to build AI agents using the [Cajun Actor Framework](https://github.com/CajunSystems/cajun). It implements an interactive appointment scheduling assistant powered by OpenAI's GPT-4.

## What You'll Learn

This example showcases:
- **Actor-based architecture** for AI agents
- **Stateful actors** that maintain conversation history
- **Async message passing** for non-blocking operations
- **Type-safe messaging** with sealed interfaces and pattern matching
- **Immutable state management** for concurrent systems

## Prerequisites

- Java 21 or higher
- OpenAI API key

## Quick Start

### 1. Clone and Setup

```bash
cd ai-agent-java-example
cp .env.example .env
```

Edit `.env` and add your OpenAI API key:
```bash
OPENAI_API_KEY=sk-proj-your-actual-api-key-here
```

### 2. Run the Agent

**macOS/Linux:**
```bash
./run.sh
```

**Windows:**
```bash
run.bat
```

**Or use Gradle directly:**
```bash
./gradlew run -q
```

### 3. Chat with the Agent

```
=================================================
   Appointment Scheduling Agent
=================================================
Type your message to interact with the agent.
Special commands:
  /slots  - Check available appointment slots
  /booked - View all booked appointments
  /quit   - Exit the application
=================================================

You: Hi, I need to book an appointment
Agent: Hello! I'd be happy to help you book an appointment. I have slots available on...

You: What's available on Tuesday?
Agent: On Tuesday, November 6th, 2025, I have the following times available: 09:00, 11:00, and 15:00.

You: /slots
Agent: Available slots:
2025-11-05: 10:00, 14:00, 16:00
2025-11-06: 09:00, 11:00, 15:00

You: Book me for Tuesday at 11:00
Agent: Great! I've booked your appointment for 2025-11-06 at 11:00

You: /booked
Agent: Booked appointments:
  - 2025-11-06 at 11:00

You: /quit
Thank you for using the Appointment Scheduling Agent. Goodbye!
```

## Architecture Overview

### Messages

All communication uses type-safe messages defined as a sealed interface:

```java
public sealed interface AppointmentMessage extends Serializable permits
    UserMessage,
    LLMResponse,
    GetAvailableSlots,
    BookAppointment {}
```

### State

The agent maintains immutable state containing conversation history and available slots:

```java
public record AppointmentState(
    List<Message> conversationHistory,
    Map<String, List<String>> availableSlots,
    List<Booking> confirmedBookings
) implements Serializable { }
```

### Handler

The `AppointmentAgentHandler` processes messages using pattern matching:

```java
return switch (message) {
    case UserMessage um -> handleUserMessage(um, state, context);
    case GetAvailableSlots gas -> handleGetSlots(gas, state, context);
    case GetBookedAppointments gba -> handleGetBookings(gba, state, context);
    case BookAppointment ba -> handleBooking(ba, state, context);
    case LLMResponse lr -> state.addMessage("assistant", lr.content());
};
```

### AI-Powered Booking

When the AI detects booking intent from the user, it responds with a special command format:
```
BOOK:2025-11-06:09:00
```

The agent parses this command and automatically triggers a `BookAppointment` message to actually book the slot. This ensures that:
- The AI handles natural language understanding
- The actor system handles the actual state mutation
- Bookings are properly persisted and tracked

## Key Actor Model Benefits

- **Isolation**: Each actor has its own state, no shared mutable data
- **Concurrency**: Actors process messages asynchronously without locks
- **Fault Tolerance**: Built-in message journaling and state snapshots
- **Type Safety**: Compile-time validation of message types
- **Scalability**: Easy to distribute actors across threads or machines

## Project Structure

```
src/main/java/com/example/appointment/
├── AppointmentMessage.java        # Message protocol
├── AppointmentState.java          # Immutable state
├── AppointmentAgentHandler.java   # Main actor logic
├── ResponseHandler.java           # Response display actor
└── AppointmentSchedulerDemo.java  # Interactive console app
```

## Customization

**Add more appointment slots:**
```java
// In AppointmentState.java
private static Map<String, List<String>> initializeSlots() {
    Map<String, List<String>> slots = new HashMap<>();
    slots.put("2025-11-07", List.of("10:00", "14:00", "16:00"));
    return slots;
}
```

**Adjust AI behavior:**
```java
// In AppointmentAgentHandler.java - buildSystemPrompt()
prompt.append("You are a friendly appointment scheduling assistant...");
```

**Add new message types:**
```java
// Remember: new messages must implement Serializable
record CancelAppointment(String bookingId, Pid replyTo) 
    implements AppointmentMessage {}
```

## Building from Source

```bash
./gradlew build
```

## Technical Notes

- Uses Java 21 preview features (enabled via `--enable-preview`)
- All messages and state classes implement `Serializable` for Cajun's persistence
- HTTP client makes async calls to OpenAI API
- Jackson library handles JSON parsing

## Troubleshooting

**Application doesn't respond:**
- Wait 2-3 seconds after each message for the AI response
- Verify your OpenAI API key is valid and has credits
- Check your internet connection

**API Key errors:**
- Ensure `.env` file exists in the project root
- Verify the API key format: `OPENAI_API_KEY=sk-proj-...`
- No quotes needed around the API key value

**Build errors:**
- Ensure Java 21 or higher is installed: `java --version`
- Clean and rebuild: `./gradlew clean build`

## License

This example is provided as-is for educational purposes.

## Learn More

- [Cajun Actor Framework](https://github.com/CajunSystems/cajun)
- Read the accompanying Medium article for detailed explanations


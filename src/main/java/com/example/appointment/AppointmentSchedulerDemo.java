package com.example.appointment;


import com.cajunsystems.ActorSystem;
import com.cajunsystems.Pid;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AppointmentSchedulerDemo {
    public static List<String> chatHistory = new ArrayList<>();
    public static ConcurrentLinkedQueue<String> agentMessageQueue = new ConcurrentLinkedQueue<>();

    public static void main(String[] args) throws InterruptedException {
        // Create actor system
        ActorSystem system = new ActorSystem();

        // Create our appointment agent
        Pid agentPid = system.statefulActorOf(
                        AppointmentAgentHandler.class,
                        new AppointmentState()
                )
                .withId("appointment-agent")
                .spawn();

        // Create a simple receiver to handle responses
        Pid receiverPid = system.actorOf(ResponseHandler.class)
                .withId("receiver")
                .spawn();

        // Interactive conversation loop
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        String userInput;
        while (running) {
            clearScreen();
            printHeader();
            printChatHistory();
            System.out.print("You: ");
            try {
                while (System.in.available() == 0 && agentMessageQueue.isEmpty()) {
                    Thread.sleep(100);
                    if (!agentMessageQueue.isEmpty()) {
                        clearScreen();
                        printHeader();
                        printChatHistory();
                        System.out.print("You: ");
                    }
                }
                if (System.in.available() > 0) {
                    userInput = scanner.nextLine().trim();
                } else {
                    userInput = "";
                }
            } catch (java.io.IOException e) {
                userInput = scanner.nextLine().trim();
            }
            while (!agentMessageQueue.isEmpty()) {
                String msg = agentMessageQueue.poll();
                chatHistory.add("Agent: " + msg);
            }
            if (userInput.isEmpty()) {
                continue;
            }
            chatHistory.add("You: " + userInput);
            if (userInput.equalsIgnoreCase("/quit") || userInput.equalsIgnoreCase("exit")) {
                chatHistory.add("\nThank you for using the Appointment Scheduling Agent. Goodbye!");
                running = false;
            } else if (userInput.equalsIgnoreCase("/slots")) {
                agentPid.tell(new GetAvailableSlots(receiverPid));
                Thread.sleep(500);
            } else if (userInput.equalsIgnoreCase("/booked")) {
                agentPid.tell(new GetBookedAppointments(receiverPid));
                Thread.sleep(500);
            } else if (userInput.toLowerCase().startsWith("/cancel ")) {
                String[] parts = userInput.split("\\s+");
                if (parts.length >= 3) {
                    String date = parts[1];
                    String time = parts[2];
                    agentPid.tell(new CancelAppointment(date, time, receiverPid));
                    Thread.sleep(500);
                } else {
                    chatHistory.add("Usage: /cancel <date> <time> (e.g. /cancel 2025-11-06 09:00)");
                }
            } else {
                agentPid.tell(new UserMessage(userInput, receiverPid));
                Thread.sleep(2000);
            }
        }
        scanner.close();
        system.shutdown();
    }

    public static void addAgentMessage(String message) {
        agentMessageQueue.add(message);
    }

    private static void printHeader() {
        System.out.println("=================================================");
        System.out.println("   Appointment Scheduling Agent");
        System.out.println("=================================================");
        System.out.println("Type your message to interact with the agent.");
        System.out.println("Special commands:");
        System.out.println("  /slots  - Check available appointment slots");
        System.out.println("  /booked - View all booked appointments");
        System.out.println("  /cancel <date> <time> - Cancel an appointment (e.g. /cancel 2025-11-06 09:00)");
        System.out.println("  /quit   - Exit the application");
        System.out.println("=================================================\n");
    }

    private static void printChatHistory() {
        for (String msg : chatHistory) {
            System.out.println(msg);
        }
        System.out.println();
    }

    private static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}

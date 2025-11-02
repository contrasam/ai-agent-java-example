package com.example.appointment;


import com.cajunsystems.ActorSystem;
import com.cajunsystems.Pid;

import java.util.Scanner;

public class AppointmentSchedulerDemo {
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
        System.out.println("=================================================");
        System.out.println("   Appointment Scheduling Agent");
        System.out.println("=================================================");
        System.out.println("Type your message to interact with the agent.");
        System.out.println("Special commands:");
        System.out.println("  /slots - Check available appointment slots");
        System.out.println("  /quit  - Exit the application");
        System.out.println("=================================================\n");

        boolean running = true;
        while (running) {
            System.out.print("You: ");
            String userInput = scanner.nextLine().trim();

            if (userInput.isEmpty()) {
                continue;
            }

            if (userInput.equalsIgnoreCase("/quit") || userInput.equalsIgnoreCase("exit")) {
                System.out.println("\nThank you for using the Appointment Scheduling Agent. Goodbye!");
                running = false;
            } else if (userInput.equalsIgnoreCase("/slots")) {
                agentPid.tell(new GetAvailableSlots(receiverPid));
                Thread.sleep(500); // Give time for response
            } else {
                agentPid.tell(new UserMessage(userInput, receiverPid));
                Thread.sleep(2000); // Give time for LLM response
            }
        }

        scanner.close();
        system.shutdown();
    }
}


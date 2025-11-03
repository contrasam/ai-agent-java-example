package com.example.appointment;

import java.io.Serializable;
import java.util.*;

public record AppointmentState(List<Message> conversationHistory, Map<String, List<String>> availableSlots,
                               List<Booking> confirmedBookings) implements Serializable {
    public AppointmentState() {
        this(new ArrayList<>(), initializeSlots(), new ArrayList<>());
    }

    private static Map<String, List<String>> initializeSlots() {
        Map<String, List<String>> slots = new HashMap<>();
        slots.put("2025-11-05", List.of("10:00", "14:00", "16:00"));
        slots.put("2025-11-06", List.of("09:00", "11:00", "15:00"));
        return slots;
    }

    public AppointmentState addMessage(String role, String content) {
        List<Message> newHistory = new ArrayList<>(conversationHistory);
        newHistory.add(new Message(role, content));
        return new AppointmentState(newHistory, availableSlots, confirmedBookings);
    }

    public AppointmentState bookSlot(String date, String time) {
        List<Booking> newBookings = new ArrayList<>(confirmedBookings);
        newBookings.add(new Booking(date, time));

        Map<String, List<String>> newSlots = new HashMap<>(availableSlots);
        List<String> daySlots = new ArrayList<>(newSlots.get(date));
        daySlots.remove(time);
        newSlots.put(date, daySlots);

        return new AppointmentState(conversationHistory, newSlots, newBookings);
    }

    public boolean hasBooking(String date, String time) {
        return confirmedBookings.stream().anyMatch(b -> b.date().equals(date) && b.time().equals(time));
    }

    public AppointmentState removeBooking(String date, String time) {
        List<Booking> newBookings = new ArrayList<>(confirmedBookings);
        boolean removed = newBookings.removeIf(b -> b.date().equals(date) && b.time().equals(time));
        if (!removed) return this;
        Map<String, List<String>> newSlots = new HashMap<>(availableSlots);
        List<String> daySlots = new ArrayList<>(newSlots.getOrDefault(date, new ArrayList<>()));
        if (!daySlots.contains(time)) {
            daySlots.add(time);
            Collections.sort(daySlots);
        }
        newSlots.put(date, daySlots);
        return new AppointmentState(conversationHistory, newSlots, newBookings);
    }
}

record Message(String role, String content) implements Serializable {}
record Booking(String date, String time) implements Serializable {}

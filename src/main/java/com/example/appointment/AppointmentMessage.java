package com.example.appointment;

import com.cajunsystems.Pid;

import java.io.Serializable;

public sealed interface AppointmentMessage extends Serializable permits
    UserMessage,
    LLMResponse,
    GetAvailableSlots,
    GetBookedAppointments,
    BookAppointment,
    CancelAppointment {}

record UserMessage(String text, Pid replyTo) implements AppointmentMessage {}

record LLMResponse(String content) implements AppointmentMessage {}

record GetAvailableSlots(Pid replyTo) implements AppointmentMessage {}

record GetBookedAppointments(Pid replyTo) implements AppointmentMessage {}

record BookAppointment(String date, String time, Pid replyTo) implements AppointmentMessage {}

record CancelAppointment(String date, String time, Pid replyTo) implements AppointmentMessage {}

record AgentResponse(String message) implements Serializable {}

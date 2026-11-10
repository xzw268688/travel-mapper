package com.travelmapper.controller.trip.dto;

public record TripDeleteResponse(
        Long tripId,
        Detached detached,
        Long newTripId,
        String newTripName) {

    public record Detached(int itineraries, int notes, int expenses) {
    }
}
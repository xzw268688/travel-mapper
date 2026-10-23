package com.travelmapper.controller.trip.dto;

public record TripDeleteResponse(Long tripId, Detached detached) {

    public record Detached(int itineraries, int notes, int expenses) {
    }
}

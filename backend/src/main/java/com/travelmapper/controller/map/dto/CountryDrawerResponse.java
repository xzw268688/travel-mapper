package com.travelmapper.controller.map.dto;

import java.time.LocalDate;
import java.util.List;

public record CountryDrawerResponse(
        String countryCode,
        String countryName,
        String status,
        Summary summary,
        List<TripGroup> trips,
        Ungrouped ungrouped) {

    public record Summary(
            int itineraryCount,
            int noteCount,
            String totalExpenseCny,
            boolean hasUnconfiguredCurrency,
            List<String> unconfiguredCurrencies) {
    }

    public record TripGroup(
            Long tripId,
            String tripName,
            LocalDate startDate,
            LocalDate endDate,
            String subtotalCny,
            List<CityGroup> cities) {
    }

    public record CityGroup(
            String cityCode,
            String cityName,
            String cityLabel,
            List<ItineraryItem> itineraries,
            List<NoteItem> notes,
            String expenseCny) {
    }

    public record ItineraryItem(
            Long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            boolean expired) {
    }

    public record NoteItem(
            Long id,
            String title,
            String visibility,
            LocalDate createdAt) {
    }

    public record Ungrouped(
            List<UngroupedItinerary> itineraries,
            List<NoteItem> notes,
            List<ExpenseItem> expenses,
            String expenseCny) {
    }

    public record UngroupedItinerary(
            Long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            boolean expired,
            List<CityRef> cities) {
    }

    public record CityRef(String cityCode, String cityName) {
    }

    public record ExpenseItem(
            Long id,
            String currency,
            String amount,
            LocalDate expenseDate,
            String category) {
    }
}

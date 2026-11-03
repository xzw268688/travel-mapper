package com.travelmapper.controller.expense;

import com.travelmapper.common.api.ApiResponse;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.expense.dto.ExpenseDtos;
import com.travelmapper.service.expense.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Expenses")
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ApiResponse<ExpenseDtos.CreateResponse> create(
            @Valid @RequestBody ExpenseDtos.CreateRequest request) {
        return ApiResponse.ok(expenseService.create(CurrentUser.id(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<ExpenseDtos.Response>> list(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false) Boolean ungrouped,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = new PageRequest();
        request.setPage(page);
        request.setSize(size);
        return ApiResponse.ok(expenseService.list(CurrentUser.id(), countryCode, cityCode,
                tripId, ungrouped, category, from, to, request));
    }

    @GetMapping("/by-date")
    public ApiResponse<ExpenseDtos.DateGroupedResponse> byDate(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false) Boolean ungrouped,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(expenseService.byDate(CurrentUser.id(), countryCode, cityCode,
                tripId, ungrouped, category, from, to));
    }

    @GetMapping("/summary")
    public ApiResponse<ExpenseDtos.SummaryResponse> summary(
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(expenseService.summary(CurrentUser.id(), groupBy, countryCode,
                tripId, from, to));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseDtos.CreateResponse> update(
            @PathVariable Long id, @Valid @RequestBody ExpenseDtos.UpdateRequest request) {
        return ApiResponse.ok(expenseService.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<ExpenseDtos.DeleteResponse> delete(@PathVariable Long id) {
        return ApiResponse.ok(expenseService.delete(CurrentUser.id(), id));
    }
}

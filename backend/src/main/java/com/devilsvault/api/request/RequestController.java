package com.devilsvault.api.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
@Tag(name = "Requests", description = "Internal/external change requests")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's requests, or all by status")
    public List<RequestDto> list(@RequestParam(required = false) Request.Status status, Principal principal) {
        if (status != null) {
            return service.list(status).stream().map(RequestDto::from).toList();
        }
        return service.listForUser(principal.getName()).stream().map(RequestDto::from).toList();
    }

    @PostMapping
    @Operation(summary = "Raise a new change request")
    public RequestDto create(@Valid @RequestBody CreateRequest body, Principal principal) {
        return RequestDto.from(service.create(principal.getName(), body));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve a pending request (internal users only)")
    public RequestDto approve(@PathVariable Long id, Principal principal) {
        return RequestDto.from(service.approve(id, principal.getName()));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request (internal users only)")
    public RequestDto reject(@PathVariable Long id, Principal principal) {
        return RequestDto.from(service.reject(id, principal.getName()));
    }
}

package com.devilsvault.api.transfer;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService service;
    private final TransferQueryService queryService;

    public TransferController(TransferService service, TransferQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @PostMapping
    public TransferResponse transfer(@Valid @RequestBody TransferRequest req, Principal principal) {
        return TransferResponse.from(service.execute(principal.getName(), req));
    }

    @GetMapping
    public TransferListResponse list(
            Principal principal,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.list(principal.getName(), accountId, since, until, page, size);
    }
}

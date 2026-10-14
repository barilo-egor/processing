package net.rcetech.support.controller;

import net.rcetech.domain.repository.incidents.IncidentSpecifications;
import net.rcetech.domain.service.incidents.IncidentService;
import net.rcetech.meta.WebPath;
import net.rcetech.meta.incidents.dto.IncidentFilter;
import net.rcetech.meta.incidents.dto.IncidentResponse;
import net.rcetech.meta.incidents.dto.UpdateIncidentRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
@RequestMapping(WebPath.PRIVATE_API_PATH + "/incident")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public PagedModel<IncidentResponse> get(IncidentFilter incidentFilter, @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(
                incidentService.findAll(IncidentSpecifications.matches(incidentFilter), pageable, IncidentResponse.class)
        );
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public IncidentResponse update(Principal principal, @PathVariable Long id, @RequestBody UpdateIncidentRequest request) {
        return incidentService.resolve(id, UUID.fromString(principal.getName()), request.comment());
    }
}

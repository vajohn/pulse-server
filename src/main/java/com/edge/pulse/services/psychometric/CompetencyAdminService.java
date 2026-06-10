package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CompetencyAdminDto;
import com.edge.pulse.data.dto.psychometric.CompetencyWeightDto;
import com.edge.pulse.data.dto.psychometric.CreateCompetencyRequest;
import com.edge.pulse.data.dto.psychometric.UpdateCompetencyRequest;
import com.edge.pulse.data.dto.psychometric.UpsertCompetencyWeightRequest;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.models.psychometric.Competency;
import com.edge.pulse.data.models.psychometric.CompetencyScaleWeight;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.repositories.psychometric.CompetencyRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScaleWeightRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScoreRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompetencyAdminService {

    private final CompetencyRepository competencyRepository;
    private final CompetencyScaleWeightRepository weightRepository;
    private final CompetencyScoreRepository competencyScoreRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CompetencyAdminDto> listCompetencies() {
        List<Competency> all = competencyRepository.findAllByOrderByDisplayOrderAsc();
        if (all.isEmpty()) return List.of();

        // Batch-load all weights in a single query — prevents N+1 (one extra query per competency).
        List<UUID> ids = all.stream().map(Competency::getId).toList();
        Map<UUID, List<CompetencyScaleWeight>> weightsByCompetency =
                weightRepository.findAllByCompetencyIdIn(ids).stream()
                        // .getId() on a LAZY proxy is safe — Hibernate 6 returns the FK value without loading the entity.
                        .collect(Collectors.groupingBy(w -> w.getCompetency().getId()));

        return all.stream()
                .map(c -> toAdminDtoWithWeights(c, weightsByCompetency.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional
    public CompetencyAdminDto createCompetency(CreateCompetencyRequest req, UUID createdById) {
        Competency competency = Competency.builder()
                .name(req.name())
                .description(req.description())
                .orgContext(req.orgContext())
                .displayOrder(req.displayOrder())
                .build();
        competency = competencyRepository.save(competency);
        auditService.logAction(createdById, "CREATE_COMPETENCY",
                "competency", competency.getId(),
                auditService.buildDetail("name", req.name()), null);
        return toAdminDto(competency);
    }

    @Transactional
    public CompetencyAdminDto updateCompetency(UUID competencyId, UpdateCompetencyRequest req,
                                                UUID updatedById) {
        Competency competency = competencyRepository.findById(competencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (req.name() != null) competency.setName(req.name());
        if (req.description() != null) competency.setDescription(req.description());
        if (req.orgContext() != null) competency.setOrgContext(req.orgContext());
        if (req.displayOrder() != null) competency.setDisplayOrder(req.displayOrder());
        competency = competencyRepository.save(competency);
        auditService.logAction(updatedById, "UPDATE_COMPETENCY",
                "competency", competencyId,
                auditService.buildDetail("name", competency.getName()), null);
        return toAdminDto(competency);
    }

    @Transactional
    public void deleteCompetency(UUID competencyId, UUID deletedById) {
        Competency competency = competencyRepository.findById(competencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Delete competency_score rows first — the FK lacks ON DELETE CASCADE,
        // so the delete would fail with a DataIntegrityViolationException if scored results exist.
        competencyScoreRepository.deleteByCompetencyId(competencyId);
        competencyRepository.delete(competency);
        auditService.logAction(deletedById, "DELETE_COMPETENCY",
                "competency", competencyId,
                auditService.buildDetail("name", competency.getName()), null);
    }

    @Transactional(readOnly = true)
    public List<CompetencyWeightDto> listWeights(UUID competencyId) {
        if (!competencyRepository.existsById(competencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return weightRepository.findByCompetencyIdWithScale(competencyId).stream()
                .map(w -> new CompetencyWeightDto(
                        competencyId,
                        w.getScale().getId(),
                        w.getScale().getName(),
                        w.getWeight(),
                        w.getDirection().name()))
                .toList();
    }

    @Transactional
    public CompetencyWeightDto upsertWeight(UUID competencyId, UUID scaleId,
                                             UpsertCompetencyWeightRequest req, UUID userId) {
        Competency competency = competencyRepository.findById(competencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PsychometricScale scale = scaleRepository.findById(scaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ScoreDirection direction;
        try {
            direction = ScoreDirection.valueOf(req.direction());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "direction must be FORWARD or REVERSE");
        }

        // Delete existing row (composite PK — simplest upsert strategy)
        weightRepository.deleteByCompetencyIdAndScaleId(competencyId, scaleId);

        CompetencyScaleWeight weight = CompetencyScaleWeight.builder()
                .competency(competency)
                .scale(scale)
                .weight(req.weight())
                .direction(direction)
                .build();
        weightRepository.save(weight);

        auditService.logAction(userId, "UPSERT_COMPETENCY_WEIGHT",
                "competency_scale_weight", competencyId,
                auditService.buildDetail("scaleId", scaleId.toString(),
                        "weight", req.weight().toPlainString(),
                        "direction", req.direction()), null);

        return new CompetencyWeightDto(competencyId, scaleId, scale.getName(),
                req.weight(), direction.name());
    }

    @Transactional
    public void deleteWeight(UUID competencyId, UUID scaleId, UUID userId) {
        if (!competencyRepository.existsById(competencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        weightRepository.deleteByCompetencyIdAndScaleId(competencyId, scaleId);
        auditService.logAction(userId, "DELETE_COMPETENCY_WEIGHT",
                "competency_scale_weight", competencyId,
                auditService.buildDetail("scaleId", scaleId.toString()), null);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────────

    /** Single-competency path (create/update) — issues one extra query for weights. */
    private CompetencyAdminDto toAdminDto(Competency c) {
        List<CompetencyScaleWeight> ws = weightRepository.findByCompetencyIdWithScale(c.getId());
        return toAdminDtoWithWeights(c, ws);
    }

    /** Batch path (list) — caller pre-loads weights to avoid N+1. */
    private CompetencyAdminDto toAdminDtoWithWeights(Competency c, List<CompetencyScaleWeight> ws) {
        List<CompetencyWeightDto> weights = ws.stream()
                .map(w -> new CompetencyWeightDto(
                        c.getId(),
                        w.getScale().getId(),
                        w.getScale().getName(),
                        w.getWeight(),
                        w.getDirection().name()))
                .toList();
        return new CompetencyAdminDto(
                c.getId(), c.getName(), c.getDescription(),
                c.getOrgContext(), c.getDisplayOrder(), weights);
    }
}

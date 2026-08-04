package com.safework.reference.service;

import com.safework.reference.dto.ReferenceResponse;
import com.safework.reference.repository.ReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceService {

    private final ReferenceRepository repository;

    public ReferenceResponse getAll() {
        return new ReferenceResponse(
                repository.findIndustries(),
                repository.findSizeClasses(),
                repository.findRegions(),
                repository.findAccidentTypes(),
                repository.findWorkTypes());
    }
}

package com.satzwerk.workouts

import org.springframework.stereotype.Service

@Service
class AdvancedTechniqueService {
    fun listMetadata(): List<AdvancedTechniqueMetadataResponse> =
        AdvancedTechnique.entries.map(AdvancedTechniqueMetadataResponse::from)
}

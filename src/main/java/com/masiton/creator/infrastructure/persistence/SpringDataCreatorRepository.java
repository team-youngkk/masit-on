package com.masiton.creator.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCreatorRepository extends JpaRepository<CreatorJpaEntity, UUID> {
}

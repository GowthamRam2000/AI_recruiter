package com.ai.recruitmentai.repository;

import com.ai.recruitmentai.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findByCandidateIdFromFile(String candidateIdFromFile);

}
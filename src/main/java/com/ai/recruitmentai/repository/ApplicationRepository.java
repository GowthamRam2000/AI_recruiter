package com.ai.recruitmentai.repository;
import com.ai.recruitmentai.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByJobDescriptionId(Long jobDescriptionId);
    List<Application> findByJobDescriptionIdAndStatus(Long jobDescriptionId, String status);
    List<Application> findByJobDescriptionIdAndMatchScoreGreaterThanEqualAndStatus(Long jobDescriptionId, Double score, String status);
    Optional<Application> findByJobDescriptionIdAndCandidateId(Long jobDescriptionId, Long candidateId);
}
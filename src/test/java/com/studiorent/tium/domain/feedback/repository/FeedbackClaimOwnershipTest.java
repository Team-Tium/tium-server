package com.studiorent.tium.domain.feedback.repository;

import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FeedbackClaimOwnershipTest {
    @Autowired private FeedbackRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void 만료된_요청의_해제는_같은_통화의_새_선점을_삭제하지_않는다() {
        Feedback expiredClaim = repository.saveAndFlush(Feedback.callProcessing(1L, 10L));
        Long expiredId = expiredClaim.getId();
        repository.deleteByMemberIdAndCallIdAndCompletedFalseAndCreatedAtBefore(
                1L, 10L, expiredClaim.getCreatedAt().plusMinutes(15));
        repository.flush();
        entityManager.clear();

        Feedback replacement = repository.saveAndFlush(Feedback.callProcessing(1L, 10L));
        Long replacementId = replacement.getId();
        entityManager.clear();

        assertThat(replacementId).isNotEqualTo(expiredId);
        assertThat(repository.deleteByIdAndMemberIdAndCallIdAndCompletedFalse(expiredId, 1L, 10L)).isZero();
        entityManager.clear();
        assertThat(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(expiredId, 1L, 10L)).isEmpty();
        assertThat(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(replacementId, 1L, 10L)).isPresent();
    }

    @Test
    void 선점_ID가_같아도_회원이나_통화가_다르면_조회와_해제를_거절한다() {
        Feedback claim = repository.saveAndFlush(Feedback.callProcessing(1L, 10L));
        Long claimId = claim.getId();
        entityManager.clear();

        assertThat(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(claimId, 2L, 10L)).isEmpty();
        assertThat(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(claimId, 1L, 20L)).isEmpty();
        assertThat(repository.deleteByIdAndMemberIdAndCallIdAndCompletedFalse(claimId, 2L, 10L)).isZero();
        assertThat(repository.deleteByIdAndMemberIdAndCallIdAndCompletedFalse(claimId, 1L, 20L)).isZero();
        assertThat(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(claimId, 1L, 10L)).isPresent();
        assertThat(repository.deleteByIdAndMemberIdAndCallIdAndCompletedFalse(claimId, 1L, 10L)).isEqualTo(1);
        entityManager.clear();
        assertThat(repository.findById(claimId)).isEmpty();
    }

    @Test
    void 완료된_피드백은_미완료_선점으로_조회하거나_해제할_수_없다() {
        Feedback feedback = repository.saveAndFlush(Feedback.callProcessing(1L, 10L));
        feedback.complete(OverallQuality.GOOD, "완료된 피드백", List.of("강점"), List.of(), "", List.of());
        repository.flush();
        Long feedbackId = feedback.getId();
        entityManager.clear();

        assertThat(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(feedbackId, 1L, 10L)).isEmpty();
        assertThat(repository.deleteByIdAndMemberIdAndCallIdAndCompletedFalse(feedbackId, 1L, 10L)).isZero();
        entityManager.clear();
        assertThat(repository.findById(feedbackId)).hasValueSatisfying(saved -> {
            assertThat(saved.getCompleted()).isTrue();
            assertThat(saved.getOverallFeedback()).isEqualTo("완료된 피드백");
            assertThat(saved.getStrength()).containsExactly("강점");
        });
    }
}

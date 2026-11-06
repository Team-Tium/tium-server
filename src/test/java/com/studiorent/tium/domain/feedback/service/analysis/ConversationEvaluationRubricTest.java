package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationEvaluationRubric;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationEvaluationRubric.AssessmentStatus;
import com.studiorent.tium.domain.feedback.dto.analysis.UtterancePurpose;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationEvaluationRubricTest {

    private ConversationEvaluationRubric loadRubric() throws Exception {
        var resource = new ClassPathResource("conversationEvaluationRubric.json");
        try (var stream = resource.getInputStream()) {
            return new ObjectMapper().readValue(stream, ConversationEvaluationRubric.class);
        }
    }

    @Test
    void 기준_JSON을_DTO로_읽고_중첩된_필수_값을_검증한다() throws Exception {
        var rubric = loadRubric();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(rubric)).isEmpty();
        }
    }

    @Test
    void 현재_분석기가_반환하는_모든_목적을_누락이나_중복_없이_다룬다() throws Exception {
        var rubric = loadRubric();
        var purposes = new ArrayList<UtterancePurpose>();
        rubric.purposeCriteria().forEach(group -> purposes.add(group.purpose()));

        assertThat(purposes).containsExactlyInAnyOrderElementsOf(Arrays.stream(UtterancePurpose.values())
                .filter(p -> p != UtterancePurpose.OTHER && p != UtterancePurpose.UNKNOWN).toList());
        assertThat(rubric.commonGuidelines()).extracting(ConversationEvaluationRubric.Guideline::id)
                .contains("analysis.other", "analysis.unknown");
        assertThat(rubric.purposeCriteria()).extracting(ConversationEvaluationRubric.PurposeCriteria::purpose)
                .doesNotContain(UtterancePurpose.OTHER, UtterancePurpose.UNKNOWN);
    }

    @Test
    void 현재_지원하는_모든_관계의_추가_기준을_제공한다() throws Exception {
        assertThat(loadRubric().relationshipCriteria())
                .extracting(ConversationEvaluationRubric.RelationshipCriteria::relationship)
                .containsExactlyInAnyOrder(RelationShip.values());
    }

    @Test
    void 공통_지침과_목적_관계_기준의_ID가_중복되지_않는다() throws Exception {
        var rubric = loadRubric();
        var ids = new ArrayList<String>();
        rubric.commonGuidelines().forEach(guideline -> ids.add(guideline.id()));
        rubric.commonCriteria().forEach(criterion -> ids.add(criterion.id()));
        rubric.purposeCriteria().forEach(group -> group.criteria().forEach(criterion -> ids.add(criterion.id())));
        rubric.relationshipCriteria().forEach(group -> group.criteria().forEach(criterion -> ids.add(criterion.id())));

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void 충족_위반_비적용_판단보류를_각각_정의한다() throws Exception {
        assertThat(loadRubric().assessmentStatuses())
                .extracting(ConversationEvaluationRubric.AssessmentStatusDefinition::status)
                .containsExactlyInAnyOrder(AssessmentStatus.values());
    }

    @Test
    void 반례의_기준과_근거_ID가_실제로_존재한다() throws Exception {
        var rubric = loadRubric();
        var criterionIds = new HashSet<String>();
        rubric.commonCriteria().forEach(criterion -> criterionIds.add(criterion.id()));
        rubric.purposeCriteria().forEach(group -> group.criteria().forEach(criterion -> criterionIds.add(criterion.id())));
        rubric.relationshipCriteria().forEach(group -> group.criteria().forEach(criterion -> criterionIds.add(criterion.id())));
        assertThat(loadExamples().reviewExamples()).extracting(ConversationEvaluationReviewExamples.ReviewExample::id)
                .doesNotHaveDuplicates();

        for (var example : loadExamples().reviewExamples()) {
            var messageIds = example.messages().stream()
                    .map(ConversationEvaluationReviewExamples.ExampleUtterance::messageId).collect(Collectors.toSet());
            assertThat(messageIds).hasSize(example.messages().size());
            for (var assessment : example.expectedAssessments()) {
                assertThat(criterionIds).as(example.id()).contains(assessment.criterionId());
                assertThat(messageIds).as(example.id()).containsAll(assessment.evidenceMessageIds());
            }
        }
    }

    @Test
    void 반례는_위반일_때만_심각도와_해소여부를_설정한다() throws Exception {
        for (var example : loadExamples().reviewExamples()) {
            for (var assessment : example.expectedAssessments()) {
                if (assessment.status() == AssessmentStatus.VIOLATED) {
                    assertThat(assessment.severity()).as(example.id()).isNotNull();
                } else {
                    assertThat(assessment.severity()).as(example.id()).isNull();
                    assertThat(assessment.resolved()).as(example.id()).isFalse();
                }
            }
        }
    }

    private ConversationEvaluationReviewExamples loadExamples() throws Exception {
        var resource = new ClassPathResource("conversationEvaluationReviewExamples.json");
        try (var stream = resource.getInputStream(); var factory = Validation.buildDefaultValidatorFactory()) {
            var examples = new ObjectMapper().readValue(stream, ConversationEvaluationReviewExamples.class);
            assertThat(factory.getValidator().validate(examples)).isEmpty();
            return examples;
        }
    }
}

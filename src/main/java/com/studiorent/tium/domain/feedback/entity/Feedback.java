package com.studiorent.tium.domain.feedback.entity;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.domain.feedback.entity.enums.Status;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자의 답변이 좋았는지? 안좋았지? 평가하는부분  /Enum good, bad , ambiguous
    @Enumerated(EnumType.STRING)
    OverallQuality overallQuality;

    // 대화 내용의 판단
    String overallFeedback;

    // 강점들
    @ElementCollection
    List<String> strength;

    // 문제적 흐름
    @ElementCollection
    List<String> flowProblem;

    // 연습 포인트
    String practicePoint;

    // 상태 식별용 Enum 써야할듯 //
    @Enumerated(EnumType.STRING)
    Status status;

    //대화ID 추가할예정

    public Feedback(FeedbackResponseDTOv1 feedback) {
        this.overallFeedback = feedback.overallFeedback();
        this.overallQuality = OverallQuality.valueOf(feedback.overallQuality());
        this.strength = feedback.strength();
        this.flowProblem = feedback.flowProblem();
        this.practicePoint = feedback.practicePoint();
    }



}

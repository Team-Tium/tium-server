package com.studiorent.tium.domain.feedback.entity;

import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(
        name = "feedback",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_feedback_call_member", columnNames = {"call_id", "member_id"})
        }
)
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
    private OverallQuality overallQuality;

    // 대화 내용의 판단
    @Column(name = "overall_feedback", columnDefinition = "TEXT")
    private String overallFeedback;

    // 강점들
    @ElementCollection
    @Column(name = "strength", columnDefinition = "TEXT")
    private List<String> strength;

    // 문제적 흐름
    @ElementCollection
    @Column(name = "flow_problem", columnDefinition = "TEXT")
    private List<String> flowProblem;

    // 연습 포인트
    @Column(name = "practice_point", columnDefinition = "TEXT")
    private String practicePoint;

    @ElementCollection
    @Column(name = "conversation_point", columnDefinition = "TEXT")
    private List<String> conversationPoints;
    @Column(name = "member_id", nullable = false)
    private Long memberId;


    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "call_id")
    private Long callId;

    @Column(name = "completed")
    private Boolean completed;

    public static Feedback callProcessing(Long memberId, Long callId) {
        return Feedback.builder()
                .memberId(memberId)
                .callId(callId)
                .completed(false)
                .build();
    }

    public void complete(
            OverallQuality overallQuality,
            String overallFeedback,
            List<String> strength,
            List<String> flowProblem,
            String practicePoint,
            List<String> conversationPoints
    ) {
        this.overallQuality = overallQuality;
        this.overallFeedback = overallFeedback;
        this.strength = strength;
        this.flowProblem = flowProblem;
        this.practicePoint = practicePoint;
        this.conversationPoints = conversationPoints;
        this.completed = true;
    }
}

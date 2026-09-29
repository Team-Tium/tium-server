package com.studiorent.tium.domain.feedback.entity;

import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

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
    private OverallQuality overallQuality;

    // 대화 내용의 판단
    private String overallFeedback;

    // 강점들
    @ElementCollection
    private List<String> strength;

    // 문제적 흐름
    @ElementCollection
    private List<String> flowProblem;

    // 연습 포인트
    private String practicePoint;

    @ElementCollection
    private List<String> conversationPoints;
    @Column(name = "member_id", nullable = false)
    private Long memberId;


    @Column(name = "room_id", nullable = false)
    private Long roomId;


    //대화ID 추가할예정



}

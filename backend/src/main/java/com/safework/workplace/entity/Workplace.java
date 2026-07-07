package com.safework.workplace.entity;

import com.safework.auth.entity.Member;
import com.safework.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Workplace extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String industryMajor;       // 대업종 (제조업, 건설업 등)

    @Column(nullable = false)
    private String industryMid;         // 중업종 (금속가공, 조선업 등)

    @Column(nullable = false)
    private String region;              // 지역 (부산, 경남 등)

    @Column(nullable = false)
    private Integer workerCount;        // 근로자 수

    private String companyName;         // 사업장명

    private String address;             // 상세 주소

    public void update(String industryMajor, String industryMid,
                       String region, Integer workerCount,
                       String companyName, String address) {
        this.industryMajor = industryMajor;
        this.industryMid = industryMid;
        this.region = region;
        this.workerCount = workerCount;
        this.companyName = companyName;
        this.address = address;
    }
}
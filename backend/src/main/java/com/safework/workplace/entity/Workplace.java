package com.safework.workplace.entity;

import com.safework.auth.entity.Member;
import com.safework.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "workplace")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Workplace extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workplace_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private Member owner;

    @Column(nullable = false)
    private String name;               // 사업장명

    @Column(nullable = false)
    private String industry;           // 대업종 (제조업, 건설업 등)

    @Column(name = "sub_industry")
    private String subIndustry;        // 중업종 (금속가공, 조선업 등)

    @Column(nullable = false)
    private String region;             // 지역 (부산, 경남 등)

    @Column(name = "employee_count")
    private Integer employeeCount;     // 근로자 수

    @Column(name = "size_class", nullable = false)
    private String sizeClass;          // 규모 구분 (5인 미만 등)

    private String address;            // 상세 주소

    public void update(String industry, String subIndustry, String region,
                        Integer employeeCount, String sizeClass,
                        String name, String address) {
        this.industry = industry;
        this.subIndustry = subIndustry;
        this.region = region;
        this.employeeCount = employeeCount;
        this.sizeClass = sizeClass;
        this.name = name;
        this.address = address;
    }
}

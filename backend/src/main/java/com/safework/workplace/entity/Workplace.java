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

    @Column(name = "machine_type")
    private String machineType;        // 주요 기계·설비 종류

    @Column(name = "machine_count")
    private Integer machineCount;      // 주요 기계·설비 총수

    @Column(name = "safety_device_status")
    private String safetyDeviceStatus; // INSTALLED/PARTIAL/NONE/UNKNOWN

    @Column(name = "storage_location")
    private String storageLocation;    // 자재·물건 적재 위치

    @Column(name = "storage_method")
    private String storageMethod;      // 적재 방식 또는 높이

    public void update(String industry, String subIndustry, String region,
                        Integer employeeCount, String sizeClass,
                        String name, String address, String machineType,
                        Integer machineCount, String safetyDeviceStatus,
                        String storageLocation, String storageMethod) {
        this.industry = industry;
        this.subIndustry = subIndustry;
        this.region = region;
        this.employeeCount = employeeCount;
        this.sizeClass = sizeClass;
        this.name = name;
        this.address = address;
        this.machineType = machineType;
        this.machineCount = machineCount;
        this.safetyDeviceStatus = safetyDeviceStatus;
        this.storageLocation = storageLocation;
        this.storageMethod = storageMethod;
    }
}

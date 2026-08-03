package com.safework.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 통합 테스트 공통 베이스.
 *
 * 실제 PostgreSQL 을 컨테이너로 띄우고 저장소의 스키마 스크립트를 순서대로 적재한다.
 * 이 프로젝트는 핵심 로직이 DB 함수(fn_prevention_guide, fn_coldstart_assess)와
 * PostgreSQL enum·배열 타입에 걸쳐 있어서, 인메모리 DB 로 바꾸면 정작 깨지는 부분을
 * 검증하지 못한다.
 *
 * 원본 데이터(사고 64만건 등)는 git 에 없으므로 테스트에 필요한 최소량만 픽스처로 넣는다.
 *
 * 실행에 Docker 가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final String SCHEMA_PATH = "../database/schema/";

    /**
     * 적재 순서가 곧 의존 관계다. 순서를 바꾸면 함수가 만들어져도 호출 시점에 깨진다.
     * (SCHEMA_2 는 archive 지만 sif_case·accident_case 등 원천 테이블을 만드는 유일한 파일)
     */
    private static final List<String> SCHEMA_FILES = List.of(
            "_archive/SCHEMA_2.sql",        // accident_case, sif_case, coldstart_baseline ...
            "SCHEMA_3_service.sql",         // app_user, workplace, checklist_*, risk_assessment, report
            "SCHEMA_4_codemaster.sql",      // 코드 마스터 + 값까지 INSERT
            "SCHEMA_6_coldstart.sql",       // fn_coldstart_score
            "SCHEMA_9_checklist_v2.sql",    // work_type, evidence_cases 컬럼
            "SCHEMA_15_predict.sql",        // accident_type_dist, fn_predict_accidents
            "SCHEMA_16a_checklist_sif_pre.sql",
            "SCHEMA_16b_checklist_sif_post.sql",
            "SCHEMA_17_lawbasis.sql",       // fn_accident_law_basis, fn_diagnosis_law_basis
            "SCHEMA_18_prevention_guide.sql",
            "SCHEMA_20_hybrid_enum.sql",
            "SCHEMA_21_fix_submission_id.sql",
            "SCHEMA_22_fix_assess_columns.sql"
    );

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18").withDatabaseName("ai_safework_test");

    static {
        // 스키마 적재는 Spring 컨텍스트가 뜨기 전에 끝나야 한다.
        // (Hibernate 가 ddl-auto=validate 로 테이블을 확인하므로)
        // @BeforeAll 은 컨텍스트 생성 이후에 돌고 클래스마다 반복되므로 여기서 한 번만 한다.
        POSTGRES.start();
        try {
            loadSchemaAndFixtures();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 DB 준비에 실패했습니다.", e);
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // ML 서버를 끄고 돌린다. 켜 두면 개발자 PC 에 ML 서버가 떠 있는지에 따라
        // 검색 결과가 달라져 테스트가 흔들린다(로컬 DB 의존성을 없앤 것과 같은 이유).
        // ML 연동 자체는 폴백 동작으로 검증한다.
        registry.add("app.ml.enabled", () -> false);
    }

    private static void loadSchemaAndFixtures() throws IOException, InterruptedException {
        // 법령 검색이 pg_trgm 을, chat_session 이 pgcrypto 를 쓴다.
        exec("CREATE EXTENSION IF NOT EXISTS pgcrypto; CREATE EXTENSION IF NOT EXISTS pg_trgm;");

        for (String file : SCHEMA_FILES) {
            copyAndRun(SCHEMA_PATH + file, "/tmp/schema.sql");
        }
        // 테스트용 최소 데이터. 픽스처가 accident_case 를 넣은 뒤 분포 테이블을 다시 만들어
        // SCHEMA_15 가 빈 테이블을 집계해 둔 것을 덮는다.
        copyAndRun("src/test/resources/fixtures/test-data.sql", "/tmp/fixture.sql");

        verifyFixtures();
    }

    /**
     * 픽스처가 실제로 들어갔는지 확인한다.
     * psql 은 개별 INSERT 가 실패해도 계속 진행하므로, 조용히 비어 있으면
     * 테스트가 엉뚱한 이유로 실패해 원인을 찾기 어렵다.
     * (실제로 identity·기본값 없는 PK 때문에 INSERT 가 통째로 무시된 적이 있다)
     */
    private static void verifyFixtures() throws IOException, InterruptedException {
        Map<String, String> expected = Map.of(
                "checklist_item", "SELECT count(*) FROM checklist_item WHERE item_code LIKE 'TEST-%'",
                "law_article", "SELECT count(*) FROM law_article",
                "law_chunk", "SELECT count(*) FROM law_chunk",
                "sif_case", "SELECT count(*) FROM sif_case",
                "accident_case", "SELECT count(*) FROM accident_case",
                "accident_type_dist", "SELECT count(*) FROM accident_type_dist",
                "coldstart_baseline", "SELECT count(*) FROM coldstart_baseline");

        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            var result = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
                    "-d", POSTGRES.getDatabaseName(), "-tAc", entry.getValue());
            String count = result.getStdout().trim();
            if (count.isEmpty() || "0".equals(count)) {
                empty.add(entry.getKey());
            }
        }
        if (!empty.isEmpty()) {
            throw new IllegalStateException("테스트 픽스처가 비어 있습니다: " + empty);
        }
    }

    private static void copyAndRun(String hostPath, String containerPath)
            throws IOException, InterruptedException {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(hostPath), containerPath);
        // ON_ERROR_STOP 을 켜지 않는다. 스크립트에 '이미 존재함' 류의 무해한 오류가 섞여 있어
        // 멈추면 오히려 적재가 안 끝난다. 스키마가 실제로 맞는지는 Hibernate validate 가 잡는다.
        var result = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
                "-d", POSTGRES.getDatabaseName(), "-f", containerPath);

        // 픽스처는 '이미 존재함' 오류가 날 이유가 없으므로 오류를 그대로 보여준다.
        // (조용히 넘어가면 테이블이 빈 채로 테스트가 엉뚱하게 실패한다)
        if (hostPath.contains("fixtures") && result.getStderr().contains("ERROR")) {
            System.err.println("[fixture] " + hostPath + " 적재 중 오류:\n" + result.getStderr());
        }
    }

    private static void exec(String sql) throws IOException, InterruptedException {
        POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
                "-d", POSTGRES.getDatabaseName(), "-c", sql);
    }
}

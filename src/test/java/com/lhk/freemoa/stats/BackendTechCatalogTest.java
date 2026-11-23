package com.lhk.freemoa.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackendTechCatalogTest {

    @Test
    @DisplayName("버전만 다른 태그는 같은 기술로 찾는다")
    void find_ignoresVersion() {
        assertThat(BackendTechCatalog.find("php 8.5")).map(Tech::name).hasValue("PHP");
        assertThat(BackendTechCatalog.find("PHP")).map(Tech::name).hasValue("PHP");
        assertThat(BackendTechCatalog.find("Oracle 11g")).map(Tech::name).hasValue("Oracle");
        assertThat(BackendTechCatalog.find("Java 1.8")).map(Tech::name).hasValue("Java");
        assertThat(BackendTechCatalog.find("spring boot 3.x")).map(Tech::name).hasValue("Spring Boot");
    }

    @Test
    @DisplayName("별칭이 달라도 같은 기술, 묶음도 맞게 나온다")
    void find_aliases() {
        assertThat(BackendTechCatalog.find("MS-SQL")).map(Tech::name).hasValue("MSSQL");
        assertThat(BackendTechCatalog.find("SQL Server")).map(Tech::name).hasValue("MSSQL");
        assertThat(BackendTechCatalog.find("K8S")).map(Tech::name).hasValue("Kubernetes");
        assertThat(BackendTechCatalog.find("nodejs")).map(Tech::group).hasValue(TechGroup.LANGUAGE);
        assertThat(BackendTechCatalog.find("MariaDB")).map(Tech::group).hasValue(TechGroup.DATABASE);
    }

    @Test
    @DisplayName("분류표에 없거나 비어 있으면 empty")
    void find_unknown() {
        assertThat(BackendTechCatalog.find("React")).isEmpty();
        assertThat(BackendTechCatalog.find("제안요청")).isEmpty();
        assertThat(BackendTechCatalog.find(null)).isEmpty();
        assertThat(BackendTechCatalog.find(" ")).isEmpty();
    }

    @Test
    @DisplayName("버전 떼기: 끝의 숫자, 점, 11g 같은 표기만 뗀다")
    void stripVersion() {
        assertThat(BackendTechCatalog.stripVersion("PHP8.5")).isEqualTo("PHP");
        assertThat(BackendTechCatalog.stripVersion("ORACLE19C")).isEqualTo("ORACLE");
        assertThat(BackendTechCatalog.stripVersion("SPRINGBOOT3.X")).isEqualTo("SPRINGBOOT");
        assertThat(BackendTechCatalog.stripVersion("MYSQL")).isEqualTo("MYSQL");
    }
}

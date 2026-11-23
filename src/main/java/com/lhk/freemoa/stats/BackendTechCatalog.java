package com.lhk.freemoa.stats;

import com.lhk.freemoa.crawler.convert.SkillParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 기술 태그(SKILL_KEY) -> 대표 기술 이름 + 묶음(언어/프레임워크/DB/인프라) 분류표.
 *
 * 왜 필요한가
 *  프리모아 기술 태그는 사람이 직접 적어서 표기가 제각각이다.
 *    "php 8.5", "PHP", "php7"        -> 모두 PHP
 *    "Oracle 11g", "oracle19c"        -> 모두 Oracle
 *    "MS-SQL", "MSSQL", "SQL Server"  -> 모두 MSSQL
 *  SKILL_KEY(공백 제거 + 대문자)만으로는 "PHP8.5"와 "PHP"가 다른 기술로 세어진다.
 *  그래서 ① 키를 그대로 찾아보고 ② 없으면 끝의 버전 숫자를 떼고 다시 찾는다.
 *
 * 목록에 없는 기술을 추가하려면 static 블록에 add(...) 한 줄을 넣으면 된다.
 * 별칭(keys)은 SkillParser.toKey 결과 모양(공백 없음, 대문자)으로 적는다.
 */
public final class BackendTechCatalog {

    /**
     * 끝에 붙은 버전 표기: "8.5", "11G", "19C", "3.X", "-1.8", "V2"
     * [-_]?   구분자 있을 수도
     * V?      v2 같은 표기
     * \d[\d.X]*  숫자로 시작해서 숫자, 점, X가 이어짐
     * [A-Z]?  11g, 19c 같은 끝 글자 하나
     * $       문자열 끝
     */
    private static final Pattern VERSION_SUFFIX = Pattern.compile("[-_]?V?\\d[\\d.X]*[A-Z]?$");

    /** key: 별칭(정규화된 키), value: 대표 기술 */
    private static final Map<String, Tech> BY_KEY = new HashMap<>();

    static {
        // 언어
        add(TechGroup.LANGUAGE, "Java", "JAVA");
        add(TechGroup.LANGUAGE, "Kotlin", "KOTLIN");
        add(TechGroup.LANGUAGE, "Python", "PYTHON");
        add(TechGroup.LANGUAGE, "PHP", "PHP");
        add(TechGroup.LANGUAGE, "Node.js", "NODE.JS", "NODEJS", "NODE");
        add(TechGroup.LANGUAGE, "C#", "C#");
        add(TechGroup.LANGUAGE, "Go", "GO", "GOLANG");
        add(TechGroup.LANGUAGE, "C/C++", "C/C++", "C", "C++");
        add(TechGroup.LANGUAGE, "Ruby", "RUBY");

        // 프레임워크 / 웹 기술
        add(TechGroup.FRAMEWORK, "Spring", "SPRING", "SPRINGFRAMEWORK", "SPRINGMVC");
        add(TechGroup.FRAMEWORK, "Spring Boot", "SPRINGBOOT");
        add(TechGroup.FRAMEWORK, "전자정부프레임워크", "전자정부프레임워크", "전자정부표준프레임워크", "전자정부", "EGOV", "EGOVFRAME");
        add(TechGroup.FRAMEWORK, "JSP", "JSP", "JSP/SERVLET", "SERVLET");
        add(TechGroup.FRAMEWORK, "MyBatis", "MYBATIS", "IBATIS");
        add(TechGroup.FRAMEWORK, "JPA", "JPA", "HIBERNATE");
        add(TechGroup.FRAMEWORK, "Django", "DJANGO");
        add(TechGroup.FRAMEWORK, "Flask", "FLASK");
        add(TechGroup.FRAMEWORK, "FastAPI", "FASTAPI");
        add(TechGroup.FRAMEWORK, "Laravel", "LARAVEL");
        add(TechGroup.FRAMEWORK, "CodeIgniter", "CODEIGNITER");
        add(TechGroup.FRAMEWORK, "Express", "EXPRESS", "EXPRESS.JS", "EXPRESSJS");
        add(TechGroup.FRAMEWORK, "NestJS", "NESTJS", "NEST.JS", "NEST");
        add(TechGroup.FRAMEWORK, "ASP.NET", "ASP.NET", ".NET", "DOTNET", "ASP.NETCORE", ".NETCORE");
        add(TechGroup.FRAMEWORK, "Ruby on Rails", "RUBYONRAILS", "RAILS");

        // DB
        add(TechGroup.DATABASE, "Oracle", "ORACLE", "ORACLEDB");
        add(TechGroup.DATABASE, "MySQL", "MYSQL");
        add(TechGroup.DATABASE, "MariaDB", "MARIADB");
        add(TechGroup.DATABASE, "PostgreSQL", "POSTGRESQL", "POSTGRES", "POSTGRE");
        add(TechGroup.DATABASE, "MSSQL", "MSSQL", "MS-SQL", "SQLSERVER", "MSSQLSERVER", "MS-SQLSERVER");
        add(TechGroup.DATABASE, "MongoDB", "MONGODB", "MONGO");
        add(TechGroup.DATABASE, "Redis", "REDIS");
        add(TechGroup.DATABASE, "Tibero", "TIBERO");
        add(TechGroup.DATABASE, "SQLite", "SQLITE");

        // 인프라 / 서버
        add(TechGroup.INFRA, "AWS", "AWS", "AMAZONWEBSERVICES");
        add(TechGroup.INFRA, "Linux", "LINUX", "CENTOS", "UBUNTU", "REDHAT", "RHEL");
        add(TechGroup.INFRA, "Docker", "DOCKER");
        add(TechGroup.INFRA, "Kubernetes", "KUBERNETES", "K8S");
        add(TechGroup.INFRA, "Apache", "APACHE");
        add(TechGroup.INFRA, "Nginx", "NGINX");
        add(TechGroup.INFRA, "Tomcat", "TOMCAT", "APACHETOMCAT");
        add(TechGroup.INFRA, "Jenkins", "JENKINS");
        add(TechGroup.INFRA, "GCP", "GCP", "GOOGLECLOUD");
        add(TechGroup.INFRA, "Azure", "AZURE");
        add(TechGroup.INFRA, "Windows Server", "WINDOWSSERVER", "IIS");
    }

    private BackendTechCatalog() {
    }

    private static void add(TechGroup group, String name, String... keys) {
        Tech tech = new Tech(name, group);
        for (String key : keys) {
            BY_KEY.put(key, tech);
        }
    }

    /**
     * 기술 태그 또는 기술 이름으로 찾는다. "php 8.5", "PHP8.5", "Java", "spring boot" 모두 된다.
     * 분류표에 없으면 Optional.empty()
     */
    public static Optional<Tech> find(String skillKeyOrName) {
        if (skillKeyOrName == null || skillKeyOrName.isBlank()) {
            return Optional.empty();
        }
        String key = SkillParser.toKey(skillKeyOrName);
        Tech tech = BY_KEY.get(key);                    // ① 그대로 찾기 ("K8S"처럼 숫자가 들어간 별칭 때문에 먼저 본다)
        if (tech == null) {
            tech = BY_KEY.get(stripVersion(key));       // ② 버전 떼고 찾기 ("PHP8.5" -> "PHP")
        }
        return Optional.ofNullable(tech);
    }

    /** "PHP8.5" -> "PHP", "ORACLE11G" -> "ORACLE". 버전이 없으면 그대로 */
    static String stripVersion(String key) {
        return VERSION_SUFFIX.matcher(key).replaceFirst("");
    }
}

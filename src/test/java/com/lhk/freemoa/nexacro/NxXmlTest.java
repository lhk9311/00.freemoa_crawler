package com.lhk.freemoa.nexacro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NxXmlTest {

    private static final String NS = "http://www.nexacroplatform.com/platform/dataset";

    @Test
    @DisplayName("넥사크로 요청 XML에서 Parameters를 읽는다 (&amp; 는 & 로 복원)")
    void readParameters() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Root xmlns=\"" + NS + "\"><Parameters>"
                + "<Parameter id=\"keyword\">java &amp; spring</Parameter>"
                + "<Parameter id=\"maxPages\">2</Parameter>"
                + "<Parameter id=\"openOnly\">1</Parameter>"
                + "</Parameters></Root>";

        NxRequest req = NxXmlReader.read(xml);

        assertThat(req.getString("keyword")).isEqualTo("java & spring");
        assertThat(req.getInt("maxPages", 0)).isEqualTo(2);
        assertThat(req.getBoolean("openOnly")).isTrue();
        assertThat(req.getString("없는값")).isNull();
    }

    @Test
    @DisplayName("[보안] DOCTYPE/외부 엔티티가 들어 있으면 거부한다 (XXE 방어)")
    void rejectXxe() {
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///C:/Windows/win.ini\">]>"
                + "<Root><Parameters><Parameter id=\"k\">&x;</Parameter></Parameters></Root>";

        assertThatThrownBy(() -> NxXmlReader.read(xxe))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("숫자 파라미터에 문자가 오면 친절한 메시지로 실패")
    void invalidNumber() {
        NxRequest req = new NxRequest(java.util.Map.of("maxPages", "abc"));

        assertThatThrownBy(() -> req.getInteger("maxPages"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPages");
    }

    @Test
    @DisplayName("[보안] 응답의 < > & 는 이스케이프, 제어문자는 제거, 날짜는 yyyyMMdd, null은 Col 생략")
    void writeDataset() {
        NxDataset ds = new NxDataset("ds_list")
                .column("title", NxType.STRING, 500)
                .column("deadline", NxType.DATE, 8)
                .column("cost", NxType.INT, 10);
        ds.addRow("<script>alert(1)</script> & \u0001x", LocalDate.of(2026, 11, 30), null);

        String xml = NxXmlWriter.success(List.of(ds));

        assertThat(xml).contains("<Parameter id=\"ErrorCode\" type=\"int\">0</Parameter>");
        assertThat(xml).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; x");
        assertThat(xml).doesNotContain("<script>");
        assertThat(xml).contains("<Col id=\"deadline\">20261130</Col>");
        assertThat(xml).doesNotContain("id=\"cost\">");
    }

    @Test
    @DisplayName("에러 응답은 ErrorCode -1")
    void writeError() {
        assertThat(NxXmlWriter.error("이미 진행 중"))
                .contains("<Parameter id=\"ErrorCode\" type=\"int\">-1</Parameter>")
                .contains("이미 진행 중");
    }

    @Test
    @DisplayName("컬럼 수와 값 수가 다르면 즉시 예외")
    void columnMismatch() {
        NxDataset ds = new NxDataset("ds").column("a", NxType.STRING, 10);

        assertThatThrownBy(() -> ds.addRow("1", "2"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

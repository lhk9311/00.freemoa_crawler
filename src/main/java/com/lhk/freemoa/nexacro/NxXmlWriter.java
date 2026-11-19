package com.lhk.freemoa.nexacro;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 넥사크로가 이해하는 XML 응답을 만든다.
 *
 * <?xml version="1.0" encoding="UTF-8"?>
 * <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
 *   <Parameters>
 *     <Parameter id="ErrorCode" type="int">0</Parameter>      <- 0 이상이면 성공, 음수면 실패
 *     <Parameter id="ErrorMsg" type="string">SUCCESS</Parameter>
 *   </Parameters>
 *   <Dataset id="ds_list">
 *     <ColumnInfo><Column id="title" type="STRING" size="500"/></ColumnInfo>
 *     <Rows><Row><Col id="title">...</Col></Row></Rows>
 *   </Dataset>
 * </Root>
 *
 * 문자열을 + 로 이어 붙이지 않고 XMLStreamWriter를 쓰는 이유:
 * 공고 제목에 < > & 가 들어 있으면 XML이 깨진다. XMLStreamWriter는 &lt; &gt; &amp; 로 자동 변환(이스케이프)한다.
 */
public final class NxXmlWriter {

    public static final String NAMESPACE = "http://www.nexacroplatform.com/platform/dataset";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private NxXmlWriter() {
    }

    public static String success(List<NxDataset> datasets) {
        return write(0, "SUCCESS", datasets);
    }

    public static String error(String message) {
        return write(-1, message, List.of());
    }

    static String write(int errorCode, String errorMsg, List<NxDataset> datasets) {
        try {
            StringWriter out = new StringWriter();
            XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(out);

            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("Root");
            w.writeDefaultNamespace(NAMESPACE);

            w.writeStartElement("Parameters");
            writeParameter(w, "ErrorCode", "int", String.valueOf(errorCode));
            writeParameter(w, "ErrorMsg", "string", errorMsg == null ? "" : errorMsg);
            w.writeEndElement();

            for (NxDataset ds : datasets) {
                writeDataset(w, ds);
            }

            w.writeEndElement(); // Root
            w.writeEndDocument();
            w.close();
            return out.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("넥사크로 응답 XML 생성 실패", e);
        }
    }

    private static void writeParameter(XMLStreamWriter w, String id, String type, String value) throws XMLStreamException {
        w.writeStartElement("Parameter");
        w.writeAttribute("id", id);
        w.writeAttribute("type", type);
        w.writeCharacters(clean(value));
        w.writeEndElement();
    }

    private static void writeDataset(XMLStreamWriter w, NxDataset ds) throws XMLStreamException {
        w.writeStartElement("Dataset");
        w.writeAttribute("id", ds.id());

        w.writeStartElement("ColumnInfo");
        for (NxDataset.Column c : ds.columns()) {
            w.writeEmptyElement("Column");
            w.writeAttribute("id", c.id());
            w.writeAttribute("type", c.type().name());
            w.writeAttribute("size", String.valueOf(c.size()));
        }
        w.writeEndElement();

        w.writeStartElement("Rows");
        for (Object[] row : ds.rows()) {
            w.writeStartElement("Row");
            for (int i = 0; i < row.length; i++) {
                if (row[i] == null) {
                    continue; // 값이 없으면 Col을 생략 -> 넥사크로에서 undefined(빈 칸)
                }
                NxDataset.Column c = ds.columns().get(i);
                w.writeStartElement("Col");
                w.writeAttribute("id", c.id());
                w.writeCharacters(clean(format(row[i])));
                w.writeEndElement();
            }
            w.writeEndElement();
        }
        w.writeEndElement(); // Rows

        w.writeEndElement(); // Dataset
    }

    private static String format(Object value) {
        if (value instanceof LocalDate d) {          // 패턴 매칭 instanceof (Java 16+)
            return d.format(DATE);
        }
        if (value instanceof LocalDateTime dt) {
            return dt.format(DATETIME);
        }
        return String.valueOf(value);
    }

    /**
     * XML 1.0에서 허용되지 않는 제어문자(탭/줄바꿈 제외)를 제거한다.
     * 외부에서 수집한 텍스트에 이런 문자가 섞이면 넥사크로가 응답 전체를 파싱하지 못한다.
     */
    private static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= 0x20) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}

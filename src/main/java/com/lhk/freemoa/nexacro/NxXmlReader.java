package com.lhk.freemoa.nexacro;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 넥사크로가 보낸 XML 요청 본문에서 Parameters를 읽는다.
 *
 * 요청 예)
 * <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
 *   <Parameters>
 *     <Parameter id="keyword">java</Parameter>
 *   </Parameters>
 * </Root>
 *
 * [보안] XXE(XML External Entity) 방어
 * XML 파서는 기본 설정 그대로 쓰면 <!DOCTYPE ... <!ENTITY x SYSTEM "file:///C:/Windows/win.ini">> 같은
 * 외부 엔티티를 해석해서 서버 파일을 읽어 응답에 실어 보낼 수 있다. (OWASP Top 10 단골)
 * 그래서 DOCTYPE 선언 자체를 금지하고, 외부 엔티티/XInclude를 전부 끈다.
 */
public final class NxXmlReader {

    private NxXmlReader() {
    }

    public static NxRequest read(String xml) {
        if (xml == null || xml.isBlank()) {
            return NxRequest.empty();
        }
        try {
            Document doc = newSafeBuilder().parse(new InputSource(new StringReader(xml)));
            Map<String, String> params = new LinkedHashMap<>();

            // 네임스페이스와 상관없이 태그 이름(localName)으로 찾는다
            NodeList nodes = doc.getElementsByTagNameNS("*", "Parameter");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                params.put(el.getAttribute("id"), el.getTextContent());
            }
            return new NxRequest(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("넥사크로 요청 XML을 읽을 수 없습니다", e);
        }
    }

    private static DocumentBuilder newSafeBuilder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder builder = f.newDocumentBuilder();
        // 기본 에러 핸들러는 System.err에 "[Fatal Error]"를 따로 찍는다. 예외로만 처리하도록 바꾼다.
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }
        });
        return builder;
    }
}

package com.example.goidoan;

import org.junit.Test;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class ExcelWorkbookWriterTest {
    @Test
    public void workbookPreservesVietnameseAndEscapesSpreadsheetText() throws Exception {
        List<String> row = new ArrayList<>();
        row.add("Cơm gà & cá <đặc biệt>");
        row.add("=SUM(A1:A2)");
        row.add("Ký tự\u0000 không hợp lệ");
        while (row.size() < 28) row.add("Cột " + (row.size() + 1));
        byte[] bytes = ExcelWorkbookWriter.write(Arrays.asList(row));
        Map<String, Document> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] xml = zip.readAllBytes();
                parts.put(entry.getName(), DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new ByteArrayInputStream(xml)));
            }
        }
        assertEquals(5, parts.size());
        assertTrue(parts.containsKey("[Content_Types].xml"));
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"));
        Document sheet = parts.get("xl/worksheets/sheet1.xml");
        assertEquals(28, sheet.getElementsByTagName("c").getLength());
        assertEquals("Cơm gà & cá <đặc biệt>", sheet.getElementsByTagName("t").item(0).getTextContent());
        assertEquals("=SUM(A1:A2)", sheet.getElementsByTagName("t").item(1).getTextContent());
        assertEquals(0, sheet.getElementsByTagName("f").getLength());
        assertEquals("AB1", sheet.getElementsByTagName("c").item(27).getAttributes().getNamedItem("r").getNodeValue());
    }
}

package com.smartscheduler.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.smartscheduler.model.Timetable;

/**
 * Minimal, dependency-free PDF writer. Produces a single-page A4 PDF that lists
 * the schedule rows in a tabular text layout. This avoids pulling in an
 * external PDF library (iText / OpenPDF) so the project builds with just the
 * JDK + MySQL driver.
 *
 * Layout: 14pt title, then 11pt rows. One PDF text-show operator per row.
 */
public final class PdfExporter {

    private static final DateTimeFormatter HM_24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HM_12 = DateTimeFormatter.ofPattern("hh:mm a");

    private PdfExporter() {
    }

    public static void export(Path target, LocalDate day, List<Timetable> entries) throws IOException {
        export(target, day, entries, true);
    }

    public static void export(Path target, LocalDate day, List<Timetable> entries,
            boolean use24HourFormat) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("BT\n");
        content.append("/F1 16 Tf\n");
        content.append("72 770 Td\n");
        content.append("(Smart Daily Timetable - ").append(day).append(") Tj\n");
        content.append("/F1 11 Tf\n");
        content.append("0 -24 Td\n");
        content.append("(Time Slot               Task                              Priority   Status) Tj\n");
        content.append("0 -8 Td\n");
        content.append("(------------------------------------------------------------------------------) Tj\n");

        for (Timetable t : entries) {
            DateTimeFormatter hm = use24HourFormat ? HM_24 : HM_12;
            String slot = hm.format(t.getStartTime()) + " - " + hm.format(t.getEndTime());
            String title = pad(safe(t.getTaskTitle()), 32);
            String pri = pad(t.isBreakBlock() ? "-"
                    : (t.getTaskPriority() == null ? "" : t.getTaskPriority().name()), 10);
            String st = t.isBreakBlock() ? "BREAK"
                    : (t.getTaskStatus() == null ? "" : t.getTaskStatus().name());
            content.append("0 -16 Td\n");
            content.append("(").append(escape(pad(slot, 22) + " " + title + " " + pri + " " + st))
                    .append(") Tj\n");
        }
        content.append("ET\n");

        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        writePdf(target, stream);
    }

    // ------------------------------------------------------------------
    // PDF skeleton writer
    // ------------------------------------------------------------------
    private static void writePdf(Path target, byte[] contentStream) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (OutputStream raw = Files.newOutputStream(target)) {
            List<Long> offsets = new ArrayList<>();
            ByteCounter out = new ByteCounter(raw);

            out.writeAscii("%PDF-1.4\n%âãÏÓ\n");

            // 1 - Catalog
            offsets.add(out.count);
            out.writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // 2 - Pages
            offsets.add(out.count);
            out.writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

            // 3 - Page
            offsets.add(out.count);
            out.writeAscii("3 0 obj\n<< /Type /Page /Parent 2 0 R "
                    + "/MediaBox [0 0 595 842] /Contents 4 0 R "
                    + "/Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");

            // 4 - Content stream
            offsets.add(out.count);
            out.writeAscii("4 0 obj\n<< /Length " + contentStream.length + " >>\nstream\n");
            out.writeRaw(contentStream);
            out.writeAscii("\nendstream\nendobj\n");

            // 5 - Font
            offsets.add(out.count);
            out.writeAscii("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n");

            // xref
            long xrefStart = out.count;
            out.writeAscii("xref\n0 " + (offsets.size() + 1) + "\n");
            out.writeAscii("0000000000 65535 f \n");
            for (long off : offsets) {
                out.writeAscii(String.format("%010d 00000 n \n", off));
            }
            out.writeAscii("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\n");
            out.writeAscii("startxref\n" + xrefStart + "\n%%EOF\n");
        }
    }

    private static String pad(String s, int w) {
        if (s == null) {
            s = "";
        }
        if (s.length() > w) {
            return s.substring(0, w);
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < w) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    /**
     * OutputStream wrapper that tracks the running byte offset.
     */
    private static class ByteCounter {

        long count = 0;
        final OutputStream raw;

        ByteCounter(OutputStream raw) {
            this.raw = raw;
        }

        void writeAscii(String s) throws IOException {
            byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
            raw.write(b);
            count += b.length;
        }

        void writeRaw(byte[] b) throws IOException {
            raw.write(b);
            count += b.length;
        }
    }
}

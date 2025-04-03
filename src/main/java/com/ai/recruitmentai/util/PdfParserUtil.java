package com.ai.recruitmentai.util;
import com.ai.recruitmentai.exception.FileParsingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayOutputStream; 
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
public final class PdfParserUtil {
    private static final Logger log=LoggerFactory.getLogger(PdfParserUtil.class);
    private PdfParserUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    public static String extractText(InputStream inputStream) {
        if (inputStream == null) {
            log.error("Input stream provided to PDF parser is null.");
            throw new FileParsingException("Cannot parse PDF from null input stream.");
        }
        byte[] pdfBytes;
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            int nRead;
            byte[] data=new byte[1024]; 
            while ((nRead=inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            pdfBytes=buffer.toByteArray();
            log.debug("Read {} bytes from input stream.", pdfBytes.length);
        } catch (IOException e) {
            log.error("Failed to read PDF InputStream into byte array", e);
            throw new FileParsingException("Error reading PDF input stream.", e);
        } finally {

        }
        try (PDDocument document=Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) { 
            PDFTextStripper pdfStripper=new PDFTextStripper();
            String text=pdfStripper.getText(document);
            log.info("Successfully extracted text from PDF input stream (via byte array).");
            return text;
        } catch (IOException e) {
            log.error("Failed to parse PDF from byte array using Loader", e);
            throw new FileParsingException("Error parsing PDF content from input stream.", e);
        }
    }

    public static String extractText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.error("Invalid file provided to PDF parser: {}", file != null ? file.getAbsolutePath() : "null");
            throw new FileParsingException("PDF file does not exist or is invalid.");
        }
        try (PDDocument document=Loader.loadPDF(file)) { 
            PDFTextStripper pdfStripper=new PDFTextStripper();
            String text=pdfStripper.getText(document);
            log.info("Successfully extracted text from PDF file: {}", file.getName());
            return text;
        } catch (IOException e) {
            log.error("Failed to parse PDF file: {}", file.getAbsolutePath(), e);
            throw new FileParsingException("Error reading or parsing PDF content from file: " + file.getName(), e);
        }
    }
}
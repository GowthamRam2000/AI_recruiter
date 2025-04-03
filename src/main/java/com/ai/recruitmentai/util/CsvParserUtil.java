package com.ai.recruitmentai.util;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.FileParsingException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public final class CsvParserUtil {
    private static final Logger log = LoggerFactory.getLogger(CsvParserUtil.class);
    private CsvParserUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    public static List<JobDescription> parseJobDescriptions(InputStream inputStream) {
        List<JobDescription> jobDescriptions = new ArrayList<>();
        if (inputStream == null) {
            log.error("Input stream provided to CSV parser is null.");
            throw new FileParsingException("Cannot parse CSV from null input stream.");
        }
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {
            String[] nextRecord;
            boolean isHeader = true;
            log.info("Starting CSV parsing for Job Descriptions.");
            while ((nextRecord = csvReader.readNext()) != null) {
                if (isHeader) {
                    isHeader = false;
                    log.debug("Skipping header row: {}", (Object) nextRecord);
                    continue;
                }
                if (nextRecord.length >= 2) {
                    String jobTitle = nextRecord[0];
                    String rawDescription = nextRecord[1];
                    if (jobTitle != null && !jobTitle.isBlank() && rawDescription != null && !rawDescription.isBlank()) {
                        JobDescription jd = new JobDescription();
                        jd.setJobTitle(jobTitle.trim());
                        jd.setRawDescription(rawDescription.trim());
                        jd.setStatus("NEW");
                        jobDescriptions.add(jd);
                        log.debug("Parsed Job Description: Title='{}'", jd.getJobTitle());
                    } else {
                        log.warn("Skipping row due to blank title or description: {}", (Object) nextRecord);
                    }
                } else {
                    log.warn("Skipping row due to insufficient columns (expected at least 2): {}", (Object) nextRecord);
                }
            }
            log.info("Finished CSV parsing. Found {} valid job descriptions.", jobDescriptions.size());
        } catch (IOException e) {
            log.error("Error reading CSV data from input stream", e);
            throw new FileParsingException("Error reading CSV data.", e);
        } catch (CsvValidationException e) {
            log.error("Error validating CSV data", e);
            throw new FileParsingException("Invalid CSV format encountered.", e);
        }
        return jobDescriptions;
    }
}
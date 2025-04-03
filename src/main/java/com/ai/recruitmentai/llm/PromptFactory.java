package com.ai.recruitmentai.llm;
public final class PromptFactory {
    private PromptFactory() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    public static String createJdSummaryPrompt(String rawJdText) {
        return """
               Analyze the following Job Description text. Extract these key details:
               1.  Required Skills (as a JSON list of strings, e.g., ["Java", "Spring Boot", "SQL"])
               2.  Minimum Years of Experience required (as a number if possible, or a descriptive string like "5+ years")
               3.  Required Qualifications (e.g., degrees, certifications, as a JSON list of strings)
               4.  Key Job Responsibilities (as a JSON list of strings summarizing main duties)

               Job Description Text:
               ---
               %s
               ---

               Your response MUST be ONLY the structured JSON object containing these fields, with keys: "required_skills", "experience_years", "qualifications", "responsibilities".
               Do NOT include any introductory text, explanations, or markdown formatting like ```json. Output ONLY the valid JSON object.
               Example JSON structure: {"required_skills": ["Skill1", "Skill2"], "experience_years": "3+ years", "qualifications": ["Bachelor's Degree in CS"], "responsibilities": ["Responsibility 1", "Responsibility 2"]}
               """.formatted(rawJdText);
    }

    public static String createCvExtractionPrompt(String rawCvText) {
        return """
               Analyze the following Candidate Resume text. Extract these key details:
               1.  Candidate Name (as a string)
               2.  Email Address (as a string)
               3.  Phone Number (as a string)
               4.  Education History (as a JSON list of objects, each object containing "degree", "institution", "years")
               5.  Work Experience (as a JSON list of objects, each object containing "jobTitle", "company", "duration", "description")
               6.  Skills (as a JSON list of strings)
               7.  Certifications (as a JSON list of strings)
               8.  Achievements (as a JSON list of strings, optional)

               Resume Text:
               ---
               %s
               ---

               Your response MUST be ONLY the structured JSON object containing these fields, with keys: "name", "email", "phone", "education", "work_experience", "skills", "certifications", "achievements".
               If a field (like achievements) is not found, use an empty list [].
               Do NOT include any introductory text, explanations, or markdown formatting like ```json. Output ONLY the valid JSON object.
               """.formatted(rawCvText);
    }

    public static String createMatchingPrompt(String structuredJdJson, String structuredCvJson) {
        return """
               Compare the following structured Candidate CV JSON with the structured Job Description JSON.
               Evaluate the candidate's suitability based ONLY on the provided JSON data. Focus on:
               1.  Skills match: How well do the candidate's skills align with the required skills?
               2.  Experience match: Does the candidate's work experience duration and relevance align with the requirements?
               3.  Qualifications match: Does the candidate possess the required qualifications (degrees, certifications)?

               Job Description JSON:
               ```json
               %s
               ```

               Candidate CV JSON:
               ```json
               %s
               ```

               Based on your comparison, calculate a match score as an integer percentage between 0 and 100.
               Also, provide a brief text justification (2-3 sentences max) explaining the main reasons for your score.

               Your response MUST be ONLY a valid JSON object containing these two fields, with keys: "match_score" (integer) and "justification" (string).
               Do NOT include any introductory text, explanations, comparisons of the inputs, or markdown formatting like ```json. Output ONLY the valid JSON object.
               Example JSON structure: {"match_score": 85, "justification": "Candidate has strong skills alignment and relevant experience, but lacks one desired certification."}
               """.formatted(structuredJdJson, structuredCvJson);
    }

    public static String createInterviewEmailDraftPrompt(String candidateName, String jobTitle) {
        return """
               You are a recruitment coordinator. Draft a professional and friendly email inviting the candidate below for an interview for the specified job title at "AI Corp" (example company name).
               Keep the email concise.
               Mention the job title clearly.
               Include placeholders like "[Interview Date and Time Options]" and "[Interview Format and Details, e.g., Video Call Link]" for the user to fill in later.
               Ask the candidate to reply with their availability or any questions.

               Candidate Name: %s
               Job Title: %s

               Generate ONLY the email body text. Do not include subject line, greetings like "Dear [Name]," (assume it will be added later), or closing remarks like "Sincerely,".
               """.formatted(candidateName, jobTitle);
    }

}
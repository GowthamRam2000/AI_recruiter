# AI Recruitment Assistant (Hack the Future Submission)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Overview

This project, developed for the "Hack the Future: A Gen AI Sprint Powered by Data," is an **AI-Powered Recruitment Screening Assistant**. It tackles the inefficiencies of manual recruitment by automating key screening tasks using Generative AI.

The application allows users to upload Job Descriptions (JDs) and Candidate CVs (Resumes). It then leverages a locally running Large Language Model (specifically Google's Gemma model served via Ollama) to:

1.  **Summarize JDs:** Extracting key requirements like skills, experience, and qualifications into structured JSON.
2.  **Parse CVs:** Extracting structured data like contact info, work history, education, and skills from unstructured PDF resumes into JSON.
3.  **Match Candidates:** Comparing the structured CV data against the summarized JD to generate a relevance score and justification using the LLM.

The system stores all data in a PostgreSQL database and provides a web interface (built with Java, Spring Boot, Thymeleaf, and Bootstrap) to manage the workflow, including uploading documents, triggering AI processing, viewing results, shortlisting candidates based on the AI-generated score, and initiating interview invitations with AI-drafted content.

## Key Features

* **Bulk Job Description Upload:** Ingest multiple JDs from a CSV file.
* **AI JD Summarization:** On-demand analysis of JDs to extract key requirements using Gemma.
* **Bulk CV Upload:** Upload multiple candidate CVs (PDF format).
* **Asynchronous AI CV Parsing:** Background processing extracts structured data from PDFs using Gemma.
* **AI Candidate Matching:** Generate relevance scores and justifications by comparing parsed CVs to summarized JDs using Gemma.
* **Threshold-Based Shortlisting:** Automatically identify top candidates based on a configurable match score.
* **AI Email Drafting:** Generate personalized interview invitation email content for shortlisted candidates using Gemma.
* **Email Integration:** Send drafted emails via a configured SMTP server (e.g., Gmail).
* **Web Interface:** View jobs, candidates, application statuses, match scores, justifications, and AI-extracted data. Trigger processing workflows.

## Technology Stack

* **Backend:**
    * Java 17+ (Tested with OpenJDK 23)
    * Spring Boot 3.x (Web, Data JPA, Mail, Validation, Async)
    * Hibernate ORM / Spring Data JPA
* **Database:**
    * PostgreSQL (Recommended)
    * PostgreSQL JDBC Driver
    * HikariCP (Connection Pooling)
* **AI / LLM:**
    * Ollama (Local LLM Server)
    * Google Gemma (e.g., `gemma:7b` or `gemma:2b` via Ollama)
* **Frontend:**
    * Thymeleaf + Thymeleaf Layout Dialect
    * HTML5 / CSS3 / JavaScript (ES6+)
    * Bootstrap 5 (Styling & Components)
* **Data Handling / Utilities:**
    * Jackson Databind (JSON)
    * Apache PDFBox (PDF Text Extraction)
    * OpenCSV (CSV Parsing)
    * Project Lombok (Optional)
* **Build Tool:**
    * Apache Maven (or Gradle)
* **Development Tools:**
    * IntelliJ IDEA
    * Git / GitHub
    * Postman / curl (API Testing)
    * PostgresApp / pgAdmin / DBeaver (DB Management)

## Architecture Overview

The application utilizes a standard Spring Boot layered architecture:

* **Controllers:** Handle HTTP requests for the REST API (`/api/*`) and the server-side rendered UI (`/ui/*`).
* **Services:** Encapsulate business logic. Key services act like specialized "agents":
    * `JobService`: Handles JD loading and summarization requests, interacting with `OllamaClient`.
    * `CandidateService`: Handles CV storage, triggers async parsing, interacts with `PdfParserUtil` and `OllamaClient`.
    * `MatchingService`: Orchestrates the comparison logic, calling `OllamaClient`.
    * `ApplicationService`: Manages application records and the shortlisting logic.
    * `InterviewService`: Coordinates email drafting (`OllamaClient`) and sending (`JavaMailSender`).
* **LLM Client (`OllamaClient`):** A dedicated service to interact with the Ollama API, sending prompts and parsing responses.
* **Repositories:** Spring Data JPA interfaces handle database interactions.
* **Entities:** JPA entities define the database table structure.
* **Utils:** Helper classes for file operations and data parsing.
* **Async Processing:** CV parsing is handled asynchronously using Spring's `@Async` and a configured `ThreadPoolTaskExecutor` to manage resources during bulk uploads.

## Local Setup & Running Instructions

Follow these steps to run the project locally:

**1. Prerequisites:**

* **Java JDK:** Version 17 or higher installed. Verify with `java -version`.
* **Maven:** Apache Maven installed. Verify with `mvn -version`. (Or Gradle)
* **Git:** Git command-line tool installed.
* **Ollama:** Download and install Ollama for your OS from [https://ollama.com/](https://ollama.com/).
* **Gemma Model:** Pull the desired Gemma model via Ollama. Open your terminal and run (choose one based on your resources):
    ```bash
    ollama pull gemma3:4b # Recommended if you have >8GB RAM + GPU
    # OR
    ollama pull gemma3:1b # Lighter version if needed
    ```
    Verify the exact model tag available using `ollama list`. **Ensure the Ollama application/server is running** before starting the Spring Boot app.
* **PostgreSQL:** Install and run a PostgreSQL server (Version 12+ recommended).
    * **macOS:** Using [PostgresApp](https://postgresapp.com/) is convenient. Ensure the app is running.
    * **Other OS / Methods:** Use official installers or package managers (e.g., `apt`, `yum`, Docker).
* **Database & User:** Create a dedicated database and user for the application. Using `psql` (connect as an admin user):
    ```sql
    CREATE DATABASE recruitment_ai_db;
    CREATE USER recruitment_user WITH PASSWORD 'YourSecurePassword123!';
    GRANT ALL PRIVILEGES ON DATABASE recruitment_ai_db TO recruitment_user;
    recruitment_ai_db 
    GRANT USAGE, CREATE ON SCHEMA public TO recruitment_user;
    \q
    ```

**2. Clone Repository:**

```bash
git clone [https://github.com/GowthamRam2000/AI_recruiter.git](https://github.com/GowthamRam2000/AI_recruiter.git)
cd AI_recruiter

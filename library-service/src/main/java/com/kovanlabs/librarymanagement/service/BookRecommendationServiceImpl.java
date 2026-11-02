package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.dto.BookAvailabilityStatus;
import com.kovanlabs.librarymanagement.dto.BookRecommendationRequest;
import com.kovanlabs.librarymanagement.dto.BookRecommendationResponse;
import com.kovanlabs.librarymanagement.dto.RecommendationIntent;
import com.kovanlabs.librarymanagement.dto.RecommendedBookItem;
import com.kovanlabs.librarymanagement.mapping.BookRecommendationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link BookRecommendationService} using Spring AI ChatClient with Google Gemini.
 * Enforces strict book recommendation scope, rejects unrelated queries, and performs real-time
 * MySQL inventory matching and availability calculations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookRecommendationServiceImpl implements BookRecommendationService {

    private final ChatClient chatClient;
    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9 ]");

    // Fast-path heuristic filter for queries clearly unrelated to books / reading
    private static final List<String> OUT_OF_SCOPE_PHRASES = List.of(
            "write code", "python code", "java code", "javascript code", "write a function",
            "debug this", "solve equation", "what is the weather", "current weather",
            "cook pasta", "how to bake", "translate to spanish", "tell me a joke",
            "who won the match", "write an essay", "system prompt", "ignore previous instructions"
    );

    @Override
    public BookRecommendationResponse getRecommendations(BookRecommendationRequest request) {
        if (Objects.isNull(request) || !StringUtils.hasText(request.query())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be blank");
        }

        String userQuery = request.query().trim();

        // 1. Guardrail / Intent Classification
        RecommendationIntent intent = classifyIntent(userQuery);
        if (intent == RecommendationIntent.OUT_OF_SCOPE) {
            log.info("Query '{}' classified as OUT_OF_SCOPE. Returning predefined response.", userQuery);
            return BookRecommendationResponse.outOfScope();
        }

        // 2. Query Library Database Catalog Candidates
        List<Book> catalogCandidates = getCatalogCandidates(userQuery);

        // 3. Fetch AI Recommendations with Library Database Catalog Context
        List<AiRawRecommendation> aiRecommendations = fetchAiRecommendations(userQuery, catalogCandidates);
        if (aiRecommendations.isEmpty()) {
            log.warn("Gemini returned no recommendations for query '{}'", userQuery);
            return BookRecommendationResponse.success(
                    "No specific book recommendations found for your query. Try refining your topic or genre.",
                    List.of()
            );
        }

        // 4. MySQL Catalog Verification & Availability Check
        List<RecommendedBookItem> items = matchWithCatalog(aiRecommendations);

        String successMessage = "Recommended books based on your interest: \"" + userQuery + "\"";
        return BookRecommendationResponse.success(successMessage, items);
    }

    /**
     * Retrieves catalog candidate books from the MySQL database to provide as context to Gemini.
     */
    private List<Book> getCatalogCandidates(String query) {
        List<Book> candidates = new ArrayList<>();
        try {
            // 1. Search books matching query keywords
            if (StringUtils.hasText(query)) {
                Page<Book> searchPage = bookRepository.searchBooks(query, null, PageRequest.of(0, 10, Sort.by("id")));
                if (searchPage != null && !searchPage.isEmpty()) {
                    candidates.addAll(searchPage.getContent());
                }
            }

            // 2. Also check if the query mentions any specific category
            for (BookCategory category : BookCategory.values()) {
                if (query != null && (query.toUpperCase().contains(category.name()) || query.toLowerCase().contains(category.getDisplayName().toLowerCase()))) {
                    Page<Book> catPage = bookRepository.findByCategory(category, PageRequest.of(0, 10, Sort.by("id")));
                    if (catPage != null && !catPage.isEmpty()) {
                        for (Book b : catPage.getContent()) {
                            if (candidates.stream().noneMatch(c -> Objects.equals(c.getId(), b.getId()))) {
                                candidates.add(b);
                            }
                        }
                    }
                }
            }

            // 3. Provide up to 30 recent catalog books so AI can inspect available inventory
            Page<Book> allPage = bookRepository.findAll(PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "id")));
            if (allPage != null && !allPage.isEmpty()) {
                for (Book b : allPage.getContent()) {
                    if (candidates.size() >= 30) break;
                    if (candidates.stream().noneMatch(c -> Objects.equals(c.getId(), b.getId()))) {
                        candidates.add(b);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not query catalog candidates from database: {}", ex.getMessage());
        }
        return candidates;
    }

    /**
     * Determines whether the user query is strictly asking for book recommendations or reading suggestions.
     * Combines fast heuristic filtering with Gemini intent classification.
     */
    public RecommendationIntent classifyIntent(String query) {
        if (!StringUtils.hasText(query)) {
            return RecommendationIntent.OUT_OF_SCOPE;
        }

        String lowerQuery = query.toLowerCase();

        // Fast-path heuristic rejection
        for (String phrase : OUT_OF_SCOPE_PHRASES) {
            if (lowerQuery.contains(phrase)) {
                return RecommendationIntent.OUT_OF_SCOPE;
            }
        }

        // ChatClient-based Intent Classifier
        try {
            String intentPrompt = buildIntentClassificationPrompt(query);
            String aiResponse = chatClient.prompt()
                    .user(intentPrompt)
                    .call()
                    .content();

            return parseIntentResponse(aiResponse);
        } catch (Exception ex) {
            log.error("AI failure during intent classification: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI recommendation service is temporarily unavailable. Please try again later.",
                    ex
            );
        }
    }

    /**
     * Prompts Gemini via ChatClient to recommend books, strongly prioritizing our database catalog when relevant.
     */
    private List<AiRawRecommendation> fetchAiRecommendations(String query, List<Book> catalogCandidates) {
        try {
            String recommendationPrompt = buildRecommendationPrompt(query, catalogCandidates);
            String aiResponse = chatClient.prompt()
                    .user(recommendationPrompt)
                    .call()
                    .content();

            return parseRecommendationsResponse(aiResponse);
        } catch (Exception ex) {
            log.error("AI failure during book recommendation generation: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI recommendation service is temporarily unavailable. Please try again later.",
                    ex
            );
        }
    }

    /**
     * Matches raw AI recommendations against the MySQL book table and calculates real-time copy availability.
     * Only recommendations selected by the AI based on the user's request are returned.
     */
    private List<RecommendedBookItem> matchWithCatalog(List<AiRawRecommendation> rawRecommendations) {
        List<RecommendedBookItem> result = new ArrayList<>();

        for (AiRawRecommendation item : rawRecommendations) {
            Optional<Book> catalogBookOpt = findInCatalog(item.isbn(), item.title(), item.author());

            if (catalogBookOpt.isPresent()) {
                Book book = catalogBookOpt.get();
                int totalCopies = Objects.nonNull(book.getTotalBookCount()) ? book.getTotalBookCount() : 0;
                int borrowedCopies = Objects.nonNull(book.getBorrowedBookCount()) ? book.getBorrowedBookCount() : 0;
                int availableCopies = totalCopies - borrowedCopies;

                BookAvailabilityStatus status = availableCopies > 0
                        ? BookAvailabilityStatus.AVAILABLE
                        : BookAvailabilityStatus.UNAVAILABLE;

                RecommendedBookItem mapped = BookRecommendationMapper.INSTANCE.toRecommendedBookItem(
                        book,
                        item.explanation(),
                        status,
                        Math.max(0, availableCopies)
                );
                result.add(mapped);
            } else {
                RecommendedBookItem notInCatalogItem = new RecommendedBookItem(
                        item.title(),
                        item.author(),
                        item.explanation(),
                        item.isbn(),
                        BookAvailabilityStatus.NOT_IN_CATALOG,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null
                );
                result.add(notInCatalogItem);
            }
        }

        // Sort so that library catalog books (AVAILABLE, then UNAVAILABLE) appear first
        result.sort((a, b) -> {
            int priorityA = a.availabilityStatus() == BookAvailabilityStatus.AVAILABLE ? 0
                    : a.availabilityStatus() == BookAvailabilityStatus.UNAVAILABLE ? 1 : 2;
            int priorityB = b.availabilityStatus() == BookAvailabilityStatus.AVAILABLE ? 0
                    : b.availabilityStatus() == BookAvailabilityStatus.UNAVAILABLE ? 1 : 2;
            return Integer.compare(priorityA, priorityB);
        });

        return result;
    }
    private Optional<Book> findInCatalog(String isbn, String title, String author) {
        if (StringUtils.hasText(isbn)) {
            String cleanIsbn = isbn.replaceAll("[^0-9X-]", "").trim();
            if (StringUtils.hasText(cleanIsbn)) {
                Optional<Book> byIsbn = bookRepository.findByIsbn(cleanIsbn);
                if (byIsbn.isPresent()) {
                    return byIsbn;
                }
            }
        }

        if (StringUtils.hasText(title)) {
            String searchTitle = title.trim();
            if (searchTitle.contains(":")) {
                searchTitle = searchTitle.substring(0, searchTitle.indexOf(":")).trim();
            }

            Page<Book> searchResults = bookRepository.searchBooks(
                    searchTitle,
                    null,
                    PageRequest.of(0, 5, Sort.by("id"))
            );

            if (searchResults != null && !searchResults.isEmpty()) {
                for (Book b : searchResults.getContent()) {
                    if (b.getTitle() != null && b.getTitle().equalsIgnoreCase(title.trim())) {
                        return Optional.of(b);
                    }
                }
                return Optional.of(searchResults.getContent().get(0));
            }
        }

        return Optional.empty();
    }

    private String buildIntentClassificationPrompt(String query) {
        return """
                You are a strict intent classification engine for a library recommendation system.
                Classify whether the following query is asking for book recommendations or reading suggestions.
                
                Categories:
                - BOOK_RECOMMENDATION: User is asking for books to read, book suggestions, reading lists, or wants to learn/explore any topic, subject, genre, skill, hobby, or domain through books (e.g. 'I want to learn finance and investing', 'recommend good thriller novels', 'what should I read for machine learning?').
                - OUT_OF_SCOPE: User query is NOT about books or reading recommendations (e.g., asking for code, math solving, trivia, recipes, weather, personal conversation, essay writing, or non-book tasks).
                
                Query: "%s"
                
                Respond ONLY with a JSON object:
                {"intent": "BOOK_RECOMMENDATION"} or {"intent": "OUT_OF_SCOPE"}
                """.formatted(query);
    }

    private String buildRecommendationPrompt(String query, List<Book> catalogBooks) {
        String catalogJson = formatCatalogForPrompt(catalogBooks);
        return """
                You are an expert library reading assistant and book curator.
                The user has the following reading request: "%s".
                
                OUR LIBRARY DATABASE INVENTORY:
                %s
                
                INSTRUCTIONS:
                1. PRIMARY OBJECTIVE: Suggest 3 to 5 books strictly tailored to the user's specific reading request.
                2. CATALOG PREFERENCE WITH STRICT RELEVANCE:
                   - Review our library database inventory above.
                   - IF our database contains books that are GENUINELY RELEVANT to the user's requested topic, genre, or interest, you MUST recommend those database books first (use the exact title, author, and ISBN from our inventory).
                   - NEVER recommend an irrelevant database book. If a database book does not genuinely match the user's request, do NOT suggest it.
                3. EXTERNAL RECOMMENDATIONS:
                   - If our library database does NOT have enough relevant books for the user's request, recommend well-known, high-quality books that accurately match what the user is asking for.
                4. For each book provide:
                   - title: The full book title.
                   - author: The author(s).
                   - explanation: A concise 1-2 sentence explanation of why this book directly answers the user's request.
                   - isbn: The standard ISBN (use the exact catalog ISBN if chosen from our library database, otherwise standard ISBN).
                
                Respond ONLY with a JSON object in the following format:
                {
                  "recommendations": [
                    {
                      "title": "Book Title",
                      "author": "Author Name",
                      "explanation": "Why this book fits the request...",
                      "isbn": "9780000000000"
                    }
                  ]
                }
                """.formatted(query, catalogJson);
    }

    private String formatCatalogForPrompt(List<Book> catalogBooks) {
        if (catalogBooks == null || catalogBooks.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[\n");
        int count = Math.min(catalogBooks.size(), 30);
        for (int i = 0; i < count; i++) {
            Book b = catalogBooks.get(i);
            sb.append("  {\"title\": \"").append(escapeJson(b.getTitle())).append("\", ")
              .append("\"author\": \"").append(escapeJson(b.getAuthor())).append("\", ")
              .append("\"category\": \"").append(b.getCategory() != null ? b.getCategory().name() : "").append("\", ")
              .append("\"isbn\": \"").append(escapeJson(b.getIsbn())).append("\"}");
            if (i < count - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    private RecommendationIntent parseIntentResponse(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return RecommendationIntent.OUT_OF_SCOPE;
        }

        String cleaned = cleanJsonString(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (root.has("intent")) {
                String intentText = root.get("intent").asText().trim();
                if ("BOOK_RECOMMENDATION".equalsIgnoreCase(intentText)) {
                    return RecommendationIntent.BOOK_RECOMMENDATION;
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse intent JSON: '{}'. Falling back to string check.", cleaned);
            if (cleaned.contains("BOOK_RECOMMENDATION") && !cleaned.contains("OUT_OF_SCOPE")) {
                return RecommendationIntent.BOOK_RECOMMENDATION;
            }
        }

        return RecommendationIntent.OUT_OF_SCOPE;
    }

    private List<AiRawRecommendation> parseRecommendationsResponse(String rawResponse) {
        List<AiRawRecommendation> list = new ArrayList<>();
        if (!StringUtils.hasText(rawResponse)) {
            return list;
        }

        String cleaned = cleanJsonString(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode recsNode = root.has("recommendations") ? root.get("recommendations") : root;
            if (recsNode.isArray()) {
                for (JsonNode node : recsNode) {
                    String title = node.has("title") ? node.get("title").asText("").trim() : "";
                    String author = node.has("author") ? node.get("author").asText("").trim() : "";
                    String explanation = node.has("explanation") ? node.get("explanation").asText("").trim() : "";
                    String isbn = node.has("isbn") ? node.get("isbn").asText("").trim() : "";

                    if (StringUtils.hasText(title)) {
                        list.add(new AiRawRecommendation(title, author, explanation, isbn));
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse recommendations JSON: {}", cleaned, e);
        }

        return list;
    }

    private String cleanJsonString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /**
     * Internal raw recommendation record parsed from Gemini JSON output.
     */
    public record AiRawRecommendation(String title, String author, String explanation, String isbn) {}
}

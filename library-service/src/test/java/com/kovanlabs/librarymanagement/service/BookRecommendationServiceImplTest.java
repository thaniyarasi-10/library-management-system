package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.dto.BookAvailabilityStatus;
import com.kovanlabs.librarymanagement.dto.BookRecommendationRequest;
import com.kovanlabs.librarymanagement.dto.BookRecommendationResponse;
import com.kovanlabs.librarymanagement.dto.RecommendationIntent;
import com.kovanlabs.librarymanagement.dto.RecommendedBookItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookRecommendationServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private ObjectMapper objectMapper;
    private BookRecommendationServiceImpl recommendationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        recommendationService = new BookRecommendationServiceImpl(chatClient, bookRepository, objectMapper);
    }

    private void setupChatClientMocks() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    @Test
    @DisplayName("Valid query: Available book in catalog returns status AVAILABLE with correct copy calculation")
    void getRecommendations_ValidQuery_BookAvailableInCatalog() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = """
                {
                  "recommendations": [
                    {
                      "title": "The Intelligent Investor",
                      "author": "Benjamin Graham",
                      "explanation": "Great book for value investing.",
                      "isbn": "9780060555665"
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(intentJson, recsJson);

        Book catalogBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(101L)
                .title("The Intelligent Investor")
                .author("Benjamin Graham")
                .isbn("9780060555665")
                .totalBookCount(10)
                .borrowedBookCount(3)
                .coverImageUrl("https://s3.amazonaws.com/covers/investor.jpg")
                .category(BookCategory.NON_FICTION)
                .build();

        when(bookRepository.findByIsbn("9780060555665")).thenReturn(Optional.of(catalogBook));

        BookRecommendationRequest request = new BookRecommendationRequest("I want to learn finance and investing");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.BOOK_RECOMMENDATION, response.intent());
        assertEquals(1, response.recommendations().size());

        RecommendedBookItem item = response.recommendations().get(0);
        assertEquals("The Intelligent Investor", item.title());
        assertEquals("Benjamin Graham", item.author());
        assertEquals(BookAvailabilityStatus.AVAILABLE, item.availabilityStatus());
        assertEquals(7, item.availableCopies());
        assertEquals(10, item.totalCopies());
        assertEquals(3, item.borrowedCopies());
        assertEquals(catalogBook.getUuid(), item.bookUuid());
        assertEquals(catalogBook.getId(), item.bookId());
        assertEquals(BookCategory.NON_FICTION, item.category());
        assertEquals("https://s3.amazonaws.com/covers/investor.jpg", item.coverImageUrl());

        verify(bookRepository).findByIsbn("9780060555665");
    }

    @Test
    @DisplayName("Catalog books are preferred first over external books")
    void getRecommendations_PrefersCatalogBooksFirst() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = """
                {
                  "recommendations": [
                    {
                      "title": "Unknown External Book",
                      "author": "External Author",
                      "explanation": "External book.",
                      "isbn": "9781111111111"
                    },
                    {
                      "title": "Catalog Book",
                      "author": "Catalog Author",
                      "explanation": "In catalog book.",
                      "isbn": "9782222222222"
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(intentJson, recsJson);

        Book catalogBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(201L)
                .title("Catalog Book")
                .author("Catalog Author")
                .isbn("9782222222222")
                .totalBookCount(5)
                .borrowedBookCount(1)
                .category(BookCategory.TECHNOLOGY)
                .build();

        when(bookRepository.findByIsbn("9781111111111")).thenReturn(Optional.empty());
        when(bookRepository.findByIsbn("9782222222222")).thenReturn(Optional.of(catalogBook));
        when(bookRepository.searchBooks(eq("Unknown External Book"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BookRecommendationRequest request = new BookRecommendationRequest("tech books");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(2, response.recommendations().size());
        assertEquals("Catalog Book", response.recommendations().get(0).title());
        assertEquals(BookAvailabilityStatus.AVAILABLE, response.recommendations().get(0).availabilityStatus());
        assertEquals("Unknown External Book", response.recommendations().get(1).title());
        assertEquals(BookAvailabilityStatus.NOT_IN_CATALOG, response.recommendations().get(1).availabilityStatus());
    }

    @Test
    @DisplayName("Valid query: Book in catalog with 0 available copies returns UNAVAILABLE")
    void getRecommendations_ValidQuery_BookUnavailableInCatalog() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = """
                {
                  "recommendations": [
                    {
                      "title": "Clean Architecture",
                      "author": "Robert C. Martin",
                      "explanation": "Software architecture principles.",
                      "isbn": "9780134494166"
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(intentJson, recsJson);

        Book catalogBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(102L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .isbn("9780134494166")
                .totalBookCount(2)
                .borrowedBookCount(2)
                .category(BookCategory.TECHNOLOGY)
                .build();

        when(bookRepository.findByIsbn("9780134494166")).thenReturn(Optional.of(catalogBook));

        BookRecommendationRequest request = new BookRecommendationRequest("Software architecture books");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.BOOK_RECOMMENDATION, response.intent());
        assertEquals(1, response.recommendations().size());

        RecommendedBookItem item = response.recommendations().get(0);
        assertEquals(BookAvailabilityStatus.UNAVAILABLE, item.availabilityStatus());
        assertEquals(0, item.availableCopies());
        assertEquals(2, item.totalCopies());
        assertEquals(2, item.borrowedCopies());
    }

    @Test
    @DisplayName("Valid query: Recommended book not in library returns NOT_IN_CATALOG")
    void getRecommendations_ValidQuery_BookNotInCatalog() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = """
                {
                  "recommendations": [
                    {
                      "title": "Random Walk Down Wall Street",
                      "author": "Burton Malkiel",
                      "explanation": "Guide to index funds.",
                      "isbn": "9780393358384"
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(intentJson, recsJson);
        when(bookRepository.findByIsbn("9780393358384")).thenReturn(Optional.empty());
        when(bookRepository.searchBooks(eq("Random Walk Down Wall Street"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BookRecommendationRequest request = new BookRecommendationRequest("Stock market books");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.BOOK_RECOMMENDATION, response.intent());
        assertEquals(1, response.recommendations().size());

        RecommendedBookItem item = response.recommendations().get(0);
        assertEquals("Random Walk Down Wall Street", item.title());
        assertEquals("Burton Malkiel", item.author());
        assertEquals(BookAvailabilityStatus.NOT_IN_CATALOG, item.availabilityStatus());
        assertEquals(0, item.availableCopies());
        assertEquals(0, item.totalCopies());
        assertEquals(0, item.borrowedCopies());
        assertNull(item.bookUuid());
        assertNull(item.bookId());
    }

    @Test
    @DisplayName("Catalog matching: Fallback to title search when ISBN is missing or unmatched")
    void getRecommendations_MatchingByTitleSearch_WhenIsbnMissing() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = """
                {
                  "recommendations": [
                    {
                      "title": "Thinking, Fast and Slow",
                      "author": "Daniel Kahneman",
                      "explanation": "Behavioral economics classic.",
                      "isbn": ""
                    }
                  ]
                }
                """;

        when(callSpec.content()).thenReturn(intentJson, recsJson);

        Book catalogBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(103L)
                .title("Thinking, Fast and Slow")
                .author("Daniel Kahneman")
                .isbn("9780374533557")
                .totalBookCount(4)
                .borrowedBookCount(1)
                .category(BookCategory.PHILOSOPHY)
                .build();

        when(bookRepository.searchBooks(eq("Thinking, Fast and Slow"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(catalogBook)));

        BookRecommendationRequest request = new BookRecommendationRequest("Behavioral psychology books");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(1, response.recommendations().size());
        RecommendedBookItem item = response.recommendations().get(0);
        assertEquals("Thinking, Fast and Slow", item.title());
        assertEquals(BookAvailabilityStatus.AVAILABLE, item.availabilityStatus());
        assertEquals(3, item.availableCopies());
    }

    @Test
    @DisplayName("Catalog matching: Title with subtitle cleans subtitle before search")
    void getRecommendations_TitleWithSubtitle_CleansSubtitle() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = """
                ```json
                {
                  "recommendations": [
                    {
                      "title": "Sapiens: A Brief History of Humankind",
                      "author": "Yuval Noah Harari",
                      "explanation": "History of human evolution.",
                      "isbn": "9780062316097"
                    }
                  ]
                }
                ```
                """;

        when(callSpec.content()).thenReturn(intentJson, recsJson);
        when(bookRepository.findByIsbn("9780062316097")).thenReturn(Optional.empty());

        Book catalogBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(104L)
                .title("Sapiens")
                .author("Yuval Noah Harari")
                .isbn("9780062316097")
                .totalBookCount(5)
                .borrowedBookCount(0)
                .category(BookCategory.HISTORY)
                .build();

        when(bookRepository.searchBooks(eq("Sapiens"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(catalogBook)));

        BookRecommendationRequest request = new BookRecommendationRequest("Books on human history");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(1, response.recommendations().size());
        assertEquals("Sapiens", response.recommendations().get(0).title());
    }

    @Test
    @DisplayName("Out of scope: Heuristic catches obvious coding requests immediately without calling AI")
    void getRecommendations_OutOfScopeHeuristic_ReturnsOutOfScopeWithoutAiCall() {
        BookRecommendationRequest request = new BookRecommendationRequest("write python code to reverse a linked list");

        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.OUT_OF_SCOPE, response.intent());
        assertEquals(BookRecommendationResponse.OUT_OF_SCOPE_MESSAGE, response.message());
        assertTrue(response.recommendations().isEmpty());

        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("Out of scope: Gemini classifies general question as OUT_OF_SCOPE")
    void getRecommendations_OutOfScopeAiClassification_ReturnsPredefinedResponse() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"OUT_OF_SCOPE\"}";
        when(callSpec.content()).thenReturn(intentJson);

        BookRecommendationRequest request = new BookRecommendationRequest("What is the capital of France and its population?");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.OUT_OF_SCOPE, response.intent());
        assertEquals(BookRecommendationResponse.OUT_OF_SCOPE_MESSAGE, response.message());
        assertTrue(response.recommendations().isEmpty());

        verify(chatClient, times(1)).prompt();
    }

    @Test
    @DisplayName("Blank query throws 400 Bad Request")
    void getRecommendations_BlankQuery_ThrowsBadRequest() {
        assertThrows(ResponseStatusException.class, () ->
                recommendationService.getRecommendations(new BookRecommendationRequest("   ")));

        assertThrows(ResponseStatusException.class, () ->
                recommendationService.getRecommendations(null));
    }

    @Test
    @DisplayName("AI failure during intent classification throws 503 Service Unavailable")
    void getRecommendations_AiFailureOnClassification_Throws503() {
        setupChatClientMocks();

        when(callSpec.content()).thenThrow(new RuntimeException("Gemini API connection error"));

        BookRecommendationRequest request = new BookRecommendationRequest("Recommend fantasy books");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                recommendationService.getRecommendations(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("temporarily unavailable"));
    }

    @Test
    @DisplayName("AI failure during recommendations fetch throws 503 Service Unavailable")
    void getRecommendations_AiFailureOnRecommendationFetch_Throws503() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        when(callSpec.content())
                .thenReturn(intentJson)
                .thenThrow(new RuntimeException("Rate limit exceeded on Gemini"));

        BookRecommendationRequest request = new BookRecommendationRequest("Recommend sci-fi books");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                recommendationService.getRecommendations(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("temporarily unavailable"));
    }

    @Test
    @DisplayName("AI returns empty recommendations list returns success with empty list message")
    void getRecommendations_EmptyAiRecommendations_ReturnsFriendlyMessage() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = "{\"recommendations\": []}";

        when(callSpec.content()).thenReturn(intentJson, recsJson);

        BookRecommendationRequest request = new BookRecommendationRequest("Unusual very obscure topic");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.BOOK_RECOMMENDATION, response.intent());
        assertTrue(response.recommendations().isEmpty());
    }

    @Test
    @DisplayName("AI returns malformed JSON handles gracefully without throwing unhandled exception")
    void getRecommendations_MalformedJson_HandlesGracefully() {
        setupChatClientMocks();

        String intentJson = "{\"intent\": \"BOOK_RECOMMENDATION\"}";
        String recsJson = "this is not valid json at all";

        when(callSpec.content()).thenReturn(intentJson, recsJson);

        BookRecommendationRequest request = new BookRecommendationRequest("Tell me about books");
        BookRecommendationResponse response = recommendationService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(RecommendationIntent.BOOK_RECOMMENDATION, response.intent());
        assertTrue(response.recommendations().isEmpty());
    }

    @Test
    @DisplayName("Empty or null raw response handling in parser methods")
    void testEmptyRawResponses() {
        // Empty intent response
        RecommendationIntent intent = recommendationService.classifyIntent("");
        assertEquals(RecommendationIntent.OUT_OF_SCOPE, intent);

        setupChatClientMocks();

        // Fallback when intent response is not valid JSON but contains keyword
        when(callSpec.content()).thenReturn("Note: The user query is BOOK_RECOMMENDATION for books");
        RecommendationIntent fallbackIntent = recommendationService.classifyIntent("Books to learn guitar");
        assertEquals(RecommendationIntent.BOOK_RECOMMENDATION, fallbackIntent);

        // Raw recommendation text wrapped with simple triple backticks
        when(bookRepository.searchBooks(anyString(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(callSpec.content()).thenReturn(
                "{\"intent\": \"BOOK_RECOMMENDATION\"}",
                "```\n{\"recommendations\": [{\"title\": \"Guitar 101\", \"author\": \"John Doe\", \"explanation\": \"Basics\", \"isbn\": \"\"}]}\n```"
        );
        BookRecommendationResponse response = recommendationService.getRecommendations(
                new BookRecommendationRequest("Books to learn guitar"));
        assertNotNull(response);
        assertEquals(1, response.recommendations().size());
        assertEquals("Guitar 101", response.recommendations().get(0).title());
    }

    @Test
    @DisplayName("BookRecommendationResponse custom message and outOfScope factory methods")
    void testResponseFactoryMethods() {
        BookRecommendationResponse defaultOut = BookRecommendationResponse.outOfScope();
        assertEquals(RecommendationIntent.OUT_OF_SCOPE, defaultOut.intent());
        assertEquals(BookRecommendationResponse.OUT_OF_SCOPE_MESSAGE, defaultOut.message());

        BookRecommendationResponse customOut = BookRecommendationResponse.outOfScope("Custom scope rejection");
        assertEquals("Custom scope rejection", customOut.message());

        BookRecommendationResponse nullCustomOut = BookRecommendationResponse.outOfScope(null);
        assertEquals(BookRecommendationResponse.OUT_OF_SCOPE_MESSAGE, nullCustomOut.message());
    }
}

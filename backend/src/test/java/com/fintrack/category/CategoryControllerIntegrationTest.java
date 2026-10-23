package com.fintrack.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import com.fintrack.auth.JwtService;
import com.fintrack.user.User;
import com.fintrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CategoryControllerIntegrationTest {

    private static final String TEST_SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> TEST_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;
    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        userA = userRepository.saveAndFlush(new User(
                "Category User A",
                "category-a-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        userB = userRepository.saveAndFlush(new User(
                "Category User B",
                "category-b-" + UUID.randomUUID() + "@example.com",
                "test-password-hash"));

        userAToken = jwtService.generateToken(userA.getId());
        userBToken = jwtService.generateToken(userB.getId());
    }

    private MockHttpServletRequestBuilder requestAs(
            String path, String token) {
        return get(path).header("Authorization", "Bearer " + token);
    }
    
    private String jsonCategory(String name, String categoryType) {
        return """
                {
                    "name": "%s",
                    "categoryType": "%s"
                }
                """.formatted(name, categoryType);
    }
    
    @Test
    void getCategoriesReturnsSystemCategoriesAndOwnCustomCategories()
            throws Exception {

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCategory("Pet Care", "EXPENSE")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCategory("Gaming", "BOTH")))
                .andExpect(status().isCreated());

        mockMvc.perform(requestAs("/api/categories", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'Food')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Pet Care')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Gaming')]").isEmpty());
    }

    @Test
    void createCategoryReturnsCreatedAndCategoryDetails()
            throws Exception {

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCategory("Pet Care", "EXPENSE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Pet Care"))
                .andExpect(jsonPath("$.categoryType").value("EXPENSE"))
                .andExpect(jsonPath("$.system").value(false));
    }

    @Test
    void createCategoryRejectsBlankName()
            throws Exception {

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCategory("   ", "EXPENSE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoriesEndpointWithoutTokenReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotUpdateOrDeleteAnotherUsersCategory()
            throws Exception {

    	MvcResult result = mockMvc.perform(post("/api/categories")
    	        .header("Authorization", "Bearer " + userAToken)
    	        .contentType(MediaType.APPLICATION_JSON)
    	        .content(jsonCategory("Private Category", "BOTH")))
    	    .andExpect(status().isCreated())
    	    .andReturn();

    	String response = result.getResponse().getContentAsString();

    	String categoryId = response.replaceAll(
    	        ".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(put("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCategory("Renamed Category", "BOTH")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(requestAs("/api/categories", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Private Category')]")
                        .isNotEmpty());
    }
    
	@Test
	void createCategoryRejectsDuplicateCustomCategoryName() throws Exception {
	    mockMvc.perform(post("/api/categories")
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Pet Care", "EXPENSE")))
	            .andExpect(status().isCreated());
	
	    mockMvc.perform(post("/api/categories")
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Pet Care", "BOTH")))
	            .andExpect(status().isConflict());
	
	    mockMvc.perform(requestAs("/api/categories", userAToken))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$[?(@.name == 'Pet Care')]").isNotEmpty());
	}
	
	@Test
	void createCategoryRejectsDuplicateSystemCategoryName() throws Exception {
	    mockMvc.perform(post("/api/categories")
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Food", "EXPENSE")))
	            .andExpect(status().isConflict());
	}
	
	@Test
	void createCategoryRejectsInvalidCategoryType() throws Exception {
	    mockMvc.perform(post("/api/categories")
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Pet Care", "SAVINGS")))
	            .andExpect(status().isBadRequest());
	
	    mockMvc.perform(requestAs("/api/categories", userAToken))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$[?(@.name == 'Pet Care')]").isEmpty());
	}
	
	@Test
	void userCanUpdateOwnCustomCategoryThroughApi() throws Exception {
	    MvcResult result = mockMvc.perform(post("/api/categories")
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Pet Care", "EXPENSE")))
	            .andExpect(status().isCreated())
	            .andReturn();
	
	    String response = result.getResponse().getContentAsString();
	    String categoryId = response.replaceAll(
	            ".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	
	    mockMvc.perform(put("/api/categories/" + categoryId)
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Pet Expenses", "EXPENSE")))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.id").value(categoryId))
	            .andExpect(jsonPath("$.name").value("Pet Expenses"))
	            .andExpect(jsonPath("$.categoryType").value("EXPENSE"))
	            .andExpect(jsonPath("$.system").value(false));
	}
	
	@Test
	void userCanDeleteOwnUnusedCustomCategoryThroughApi() throws Exception {
	    MvcResult result = mockMvc.perform(post("/api/categories")
	                    .header("Authorization", "Bearer " + userAToken)
	                    .contentType(MediaType.APPLICATION_JSON)
	                    .content(jsonCategory("Temporary Category", "BOTH")))
	            .andExpect(status().isCreated())
	            .andReturn();
	
	    String response = result.getResponse().getContentAsString();
	    String categoryId = response.replaceAll(
	            ".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	
	    mockMvc.perform(delete("/api/categories/" + categoryId)
	                    .header("Authorization", "Bearer " + userAToken))
	            .andExpect(status().isNoContent());
	
	    mockMvc.perform(requestAs("/api/categories", userAToken))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath(
	                    "$[?(@.name == 'Temporary Category')]").isEmpty());
	}


}

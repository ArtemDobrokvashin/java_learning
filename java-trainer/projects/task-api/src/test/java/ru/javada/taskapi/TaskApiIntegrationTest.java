package ru.javada.taskapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:tasktest;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TaskRepository repository;

    @BeforeEach
    void resetDatabase() { repository.deleteAll(); }

    @Test
    void createThenList() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Написать тест\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("Написать тест"))
            .andExpect(jsonPath("$.done").value(false));
        mvc.perform(get("/api/tasks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Написать тест"));
    }

    @Test
    void rejectBlankTitleAndMissingId() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/{id}", 9999))
            .andExpect(status().isNotFound());
    }

    @Test
    void markAsDoneAndPersist() throws Exception {
        Long id = repository.save(new Task("Закончить проект")).getId();
        // Red until you implement PATCH /api/tasks/{id}/done.
        mvc.perform(patch("/api/tasks/{id}/done", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.done").value(true));
        mvc.perform(get("/api/tasks/{id}", id))
            .andExpect(jsonPath("$.done").value(true));
    }
}

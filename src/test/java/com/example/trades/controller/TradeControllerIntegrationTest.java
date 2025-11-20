package com.example.trades.controller;

import com.example.trades.InstructionsCaptureApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = InstructionsCaptureApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TradeControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void uploadCsv_returnsAccountingTradesAndPublishesToKafka() throws IOException {
        String url = "http://localhost:" + port + "/api/trades/upload";

        ClassPathResource resource = new ClassPathResource("sample-trades.csv");
        byte[] bytes = resource.getInputStream().readAllBytes();

        ByteArrayResource contentsAsResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "sample-trades.csv";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", contentsAsResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(kafkaTemplate, times(2)).send(anyString(), any(), any());
    }
}

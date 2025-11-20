package com.example.trades.controller;

import com.example.trades.model.PlatformTrade;
import com.example.trades.model.TradeInstruction;
import com.example.trades.service.TradeTransformationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/trades")
@Validated
@Tag(name = "Trades", description = "Endpoints for uploading trade instructions")
public class TradeController {

    private final TradeTransformationService transformationService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String outboundTopic;

    public TradeController(TradeTransformationService transformationService,
                           KafkaTemplate<Object, Object> kafkaTemplate,
                           @Value("${app.kafka.outbound-topic:instructions.outbound}") String outboundTopic) {
        this.transformationService = transformationService;
        this.kafkaTemplate = kafkaTemplate;
        this.outboundTopic = outboundTopic;
    }

    @Operation(summary = "Upload trade instructions file (CSV or JSON Format)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PlatformTrade>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ResponseEntity.badRequest().build();
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        List<PlatformTrade> result;

        if (lower.endsWith(".csv")) {
            result = processCsv(file);
        } else if (lower.endsWith(".json")) {
            result = processJson(file);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(result);
    }

    private List<PlatformTrade> processCsv(MultipartFile file) throws IOException {
        List<PlatformTrade> trades = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .parse(reader);

            for (CSVRecord record : parser) {
                Map<String, Object> raw = new HashMap<>();
                raw.put("instructionId", record.get("instructionId"));
                raw.put("account_number", record.get("account_number"));
                raw.put("security_id", record.get("security_id"));
                raw.put("trade_type", record.get("trade_type"));
                raw.put("quantity", record.get("quantity"));
                raw.put("price", record.get("price"));
                //raw.put("tradeTimestamp", record.get("tradeTimestamp"));

                TradeInstruction canonical = transformationService.toCanonical(raw);
                PlatformTrade accountingTrade = transformationService.toAccountingJson(canonical);
                String json = objectMapper.writeValueAsString(accountingTrade);
                kafkaTemplate.send(outboundTopic, canonical.getInstructionId(), json);
                trades.add(accountingTrade);
            }
        }
        return trades;
    }

    private List<PlatformTrade> processJson(MultipartFile file) throws IOException {
        List<PlatformTrade> trades = new ArrayList<>();
        List<Map<String, Object>> rawList = objectMapper.readValue(
                file.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
        for (Map<String, Object> raw : rawList) {
            TradeInstruction canonical = transformationService.toCanonical(raw);
            PlatformTrade accountingTrade = transformationService.toAccountingJson(canonical);
            String json = objectMapper.writeValueAsString(accountingTrade);
            kafkaTemplate.send(outboundTopic, canonical.getInstructionId(), json);
            trades.add(accountingTrade);
        }
        return trades;
    }
}

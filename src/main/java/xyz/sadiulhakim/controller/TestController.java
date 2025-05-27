package xyz.sadiulhakim.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

@RestController
public class TestController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/ping")
    ResponseEntity<?> pong() {
        return ResponseEntity.ok(Collections.singletonMap("message", "pong"));
    }

    @GetMapping("/my-ip")
    public Map<String, String> getClientIp(HttpServletRequest request) {
        String ipAddress = extractClientIp(request);
        return Map.of("ip", ipAddress);
    }

    private String extractClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isEmpty()) {
            return header.split(",")[0].trim();  // Get original client IP
        }
        return request.getRemoteAddr(); // fallback
    }

    @GetMapping("/stream")
    SseEmitter stream() {
        SseEmitter emitter = new SseEmitter((Long) 120_000L);

        Thread.ofVirtual().start(() -> {
            for (int i = 1; i <= 100; i++) {
                try {
                    emitter.send("I am on " + i, MediaType.TEXT_PLAIN);
                    TimeUnit.SECONDS.sleep(1);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    /*
     * This is much faster(compressed) than streaming
     * */
    @GetMapping("/huge-json-zip")
    public List<DataObject> streamJsonResponseZip() {
        return IntStream.range(0, 100_000)
                .mapToObj(i -> new DataObject(i, "Item " + i))
                .toList();
    }

    @GetMapping("/huge-json")
    public StreamingResponseBody streamJsonResponse() {
        List<DataObject> dataObjects = IntStream.range(0, 100_000)
                .mapToObj(i -> new DataObject(i, "Item " + i))
                .toList();

        return outputStream -> {
            try {
                // Begin JSON array
                outputStream.write("[".getBytes());

                for (int i = 0; i < dataObjects.size(); i++) {
                    DataObject dataObject = dataObjects.get(i);

                    // Serialize DataObject to JSON and write it to output stream
                    String json = objectMapper.writeValueAsString(dataObject);
                    outputStream.write(json.getBytes());
                    if (i < dataObjects.size() - 1) {
                        outputStream.write(",".getBytes());  // Add comma between objects
                    }

                    outputStream.flush(); // Flush the stream after each write
                }

                // End JSON array
                outputStream.write("]".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    @GetMapping("/huge-json-2")
    public StreamingResponseBody streamJsonResponse2() {
        return outputStream -> {
            try (JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(outputStream)) {
                jsonGenerator.writeStartArray(); // Begin JSON array

                IntStream.range(0, 100_000).forEach(i -> {
                    try {
                        jsonGenerator.writeObject(new DataObject(i, "Item " + i));
                        jsonGenerator.flush(); // Flush after each object
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                jsonGenerator.writeEndArray(); // End JSON array
            }
        };
    }

    @GetMapping(value = "/huge-json-gzip", produces = "application/json")
    public ResponseEntity<StreamingResponseBody> streamGzipJson() {

        StreamingResponseBody response = outputStream -> {
            // Wrap the output in a GZIPOutputStream for compression
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(outputStream);
                 JsonGenerator jsonGen = objectMapper.getFactory().createGenerator(gzipOut)) {

                jsonGen.writeStartArray(); // Begin JSON array

                IntStream.range(0, 100_000).forEach(i -> {
                    try {
                        jsonGen.writeObject(new DataObject(i, "Item " + i));
                        jsonGen.flush(); // Optional: flush to push bytes early
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                jsonGen.writeEndArray(); // End JSON array
            }
        };

        return ResponseEntity.ok()
                .header("Content-Encoding", "gzip")
                .header("Content-Type", "application/json")
                .body(response);
    }

    public static class DataObject {
        public int id;
        public String name;

        public DataObject(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}

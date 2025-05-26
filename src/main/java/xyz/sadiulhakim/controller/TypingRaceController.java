package xyz.sadiulhakim.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/typing-race")
public class TypingRaceController {

    private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    @GetMapping("/subscribe/{playerId}")
    public SseEmitter subscribe(@PathVariable String playerId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        clients.put(playerId, emitter);
        emitter.onCompletion(() -> clients.remove(playerId));
        emitter.onTimeout(() -> clients.remove(playerId));
        return emitter;
    }

    @PostMapping("/progress/{playerId}/{progress}")
    public void updateProgress(@PathVariable String playerId, @PathVariable int progress) {
        progressMap.put(playerId, progress);
        broadcastProgress();
    }

    private void broadcastProgress() {
        for (Map.Entry<String, SseEmitter> entry : clients.entrySet()) {
            try {
                entry.getValue().send(progressMap);
            } catch (IOException e) {
                entry.getValue().complete();
                clients.remove(entry.getKey());
            }
        }
    }
}

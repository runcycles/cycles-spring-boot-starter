package io.runcycles.demo.client.spring.controller;

import io.runcycles.demo.client.spring.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
public class LlmController {
    private static final Logger LOG = LoggerFactory.getLogger(LlmController.class);

    @Autowired
    private LlmService llmService;

    @PostMapping("/generate")
    public String generate(
            @RequestParam String prompt,
            @RequestParam int tokens) {
        return llmService.generateText(prompt, tokens);
    }
    @PostMapping ("/chat")
    public ResponseEntity<Map<String,Object>> handleChat(@RequestBody Map<String,Object> request) {
        LOG.info("Got request to chat: request={}",request);
        long t1 = System.currentTimeMillis() ;
        try {
            String prompt = (String) request.get("prompt");
            int tokens = ((Number) request.get("tokens")).intValue();
            String result = llmService.generateText(prompt, tokens);
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("prompt", prompt);
            responseBody.put("response", result);
            long t2 = System.currentTimeMillis() ;
            LOG.info("Chat elapse time(ms): elapseTime={}ms",(t2-t1));
            return ResponseEntity.ok().body(responseBody);
        }catch (Exception e){
            LOG.error("Failed to chat: request={}",request,e);
            Map<String,Object> result = new HashMap<>();
            result.put("error",e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    @GetMapping ("/info")
    public String info (){
        return "Cycles protocol demo application" ;
    }
}
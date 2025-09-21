package com.arvatar.vortex.controller;

import com.arvatar.vortex.service.DialogueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import voxel.dialogue.v1.DialogueServiceOuterClass.*;

@RestController
@RequestMapping("/api/v1/dialogue")
@CrossOrigin(origins = "*")
public class DialogueController {

    @Autowired
    private DialogueService dialogueService;

    /**
     * Get guru profile information
     */
    @GetMapping("/guru/{guruId}/profile")
    public ResponseEntity<GetGuruProfileResponse> getGuruProfile(@PathVariable String guruId) {
        GetGuruProfileRequest request = GetGuruProfileRequest.newBuilder()
                .setGuruId(guruId)
                .build();
        
        GetGuruProfileResponse response = dialogueService.getGuruProfile(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Ask a question to the guru (streaming response)
     * Note: This is a simplified REST version. For true streaming, consider WebSocket or Server-Sent Events
     */
    @PostMapping("/guru/{guruId}/ask")
    public ResponseEntity<AnswerChunk> askQuestion(
            @PathVariable String guruId,
            @RequestBody AskRequest askRequest) {
        
        AskRequest request = AskRequest.newBuilder()
                .setGuruId(guruId)
                .setConversationId(askRequest.getConversationId())
                .setUserQuery(askRequest.getUserQuery())
                .addAllContextTags(askRequest.getContextTagsList())
                .setReturnAudio(askRequest.getReturnAudio())
                .setReturnLipsync(askRequest.getReturnLipsync())
                .build();
        
        AnswerChunk response = dialogueService.askQuestion(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Simple ask endpoint with question as query parameter
     */
    @PostMapping("/guru/{guruId}/ask-simple")
    public ResponseEntity<AnswerChunk> askSimpleQuestion(
            @PathVariable String guruId,
            @RequestParam String userQuery,
            @RequestParam(required = false) String conversationId) {
        
        AskRequest request = AskRequest.newBuilder()
                .setGuruId(guruId)
                .setUserQuery(userQuery)
                .setConversationId(conversationId != null ? conversationId : "")
                .setReturnAudio(false)
                .setReturnLipsync(false)
                .build();
        
        AnswerChunk response = dialogueService.askQuestion(request);
        return ResponseEntity.ok(response);
    }
}

package com.arvatar.vortex.service;

import com.arvatar.vortex.models.PersonaChunk;
import com.arvatar.vortex.models.PersonProfile;
import org.springframework.stereotype.Service;
import voxel.dialogue.v1.DialogueServiceOuterClass.*;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DialogueService {

    private final DatabaseWriter databaseWriter;
    private final LLMService llmService;
    private static final int RAG_TOP_K = 5;

    public DialogueService(DatabaseWriter databaseWriter,
                          LLMService llmService) {
        this.databaseWriter = databaseWriter;
        this.llmService = llmService;
    }

    /**
     * Get guru profile information
     */
    public GetGuruProfileResponse getGuruProfile(GetGuruProfileRequest request) {
        PersonProfile profile;
        try {
            profile = databaseWriter.getPersonProfile(request.getGuruId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve guru profile", e);
        }
        if (profile == null) {
            return GetGuruProfileResponse.newBuilder().build();
        }
        GetGuruProfileResponse.Builder responseBuilder = GetGuruProfileResponse.newBuilder();
        voxel.common.v1.Types.GuruProfile.Builder guruProfile = voxel.common.v1.Types.GuruProfile.newBuilder()
                .setGuruId(profile.guruId)
                .setDisplayName(profile.displayName)
                .setBiography(profile.biography != null ? profile.biography : "")
                .addTags("helpful")
                .addTags("friendly")
                .setDefaultPointCloudVariant("neutral");
        if (profile.createdAt != null) {
            guruProfile.setUpdatedAt(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(profile.createdAt.getTime() / 1000)
                    .build());
        }
        responseBuilder.setProfile(guruProfile.build());
        return responseBuilder.build();
    }
    
    public AnswerChunk askQuestion(AskRequest request) {
        try {
            float[] queryEmbedding = llmService.embedText(request.getUserQuery());
            List<PersonaChunk> chunks = databaseWriter.searchSimilarChunks(
                request.getGuruId(), 
                queryEmbedding, 
                RAG_TOP_K
            );
            String answer = llmService.generateAnswer(request.getUserQuery(), chunks);
            List<voxel.common.v1.Types.ContextReference> citations = buildCitations(chunks);
            return AnswerChunk.newBuilder()
                    .setTranscriptDelta(answer)
                    .addAllCitations(citations)
                    .setIsFinal(true)
                    .build();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve context for question", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate answer", e);
        }
    }

    private List<voxel.common.v1.Types.ContextReference> buildCitations(List<PersonaChunk> chunks) {
        return chunks.stream()
                .map(chunk -> voxel.common.v1.Types.ContextReference.newBuilder()
                        .setContextId(chunk.chunkId.toString())
                        .setSnippet(truncate(chunk.transcript, 150))
                        .setScore(1.0 - chunk.distance)
                        .build())
                .collect(Collectors.toList());
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text != null ? text : "";
        }
        return text.substring(0, maxLength) + "...";
    }
}

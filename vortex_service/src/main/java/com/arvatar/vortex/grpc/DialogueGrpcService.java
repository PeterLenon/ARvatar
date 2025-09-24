package com.arvatar.vortex.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import voxel.dialogue.v1.DialogueServiceGrpc;
import voxel.dialogue.v1.DialogueServiceOuterClass.*;
import com.arvatar.vortex.service.DialogueService;

@GrpcService
public class DialogueGrpcService extends DialogueServiceGrpc.DialogueServiceImplBase {

    @Autowired
    private DialogueService dialogueService;

    @Override
    public void getGuruProfile(GetGuruProfileRequest request, 
                              StreamObserver<GetGuruProfileResponse> responseObserver) {
        try {
            GetGuruProfileResponse response = dialogueService.getGuruProfile(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void ask(AskRequest request, StreamObserver<AnswerChunk> responseObserver) {
        try {
            // For streaming responses, we need to handle the streaming nature
            // The current DialogueService.askQuestion() returns a single AnswerChunk
            // but the gRPC service expects to stream multiple chunks
            
            // Get the initial response from the business service
            AnswerChunk initialChunk = dialogueService.askQuestion(request);
            
            // Send the initial chunk
            responseObserver.onNext(initialChunk);
            
            // TODO: Implement proper streaming logic here
            // For now, we'll simulate streaming by sending additional chunks
            if (!initialChunk.getIsFinal()) {
                // Send additional chunks if needed
                AnswerChunk.Builder additionalChunk = AnswerChunk.newBuilder()
                        .setTranscriptDelta(" (Additional streaming content)")
                        .setIsFinal(true);
                
                responseObserver.onNext(additionalChunk.build());
            }
            
            // Complete the stream
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}

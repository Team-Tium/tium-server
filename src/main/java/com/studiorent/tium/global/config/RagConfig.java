package com.studiorent.tium.global.config;


import com.studiorent.tium.domain.feedback.service.rag.ConversationFlowCaseReader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class RagConfig {


    /**
     * 문서 chunk에서 키워드 metadata를 뽑는 선택 기능.
     * 이 기능은 ChatModel을 호출하므로, 무료/로컬 모드나 서버 시작 안정성을 위해 기본 OFF로 둔다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.rag.keyword-enricher", name = "enabled", havingValue = "true")
    public DocumentTransformer keywordMetadataEnricher(ChatModel chatModel) { //자기가 키워드를 뽑아야해서 chatmodel이 필요함
        return new KeywordMetadataEnricher(chatModel, 4);  //
    }



    /**
     * ETL 결과로 VectorStore에 들어갈 Document를 콘솔에 출력하는 Writer.
     * 실제 서비스 기능에는 필수는 아니지만, 어떤 문서가 저장되는지 확인할 때 유용하다.
     */
    @Bean
    public DocumentWriter jsonConsoleDocumentWriter(ObjectMapper objectMapper) { //json 타입으로 콘솔을 찍을라면 object이 필요함
        // 앞 단계에서 가공되어  넘어온 문서 조각 리스트(document)를 받아서 로직 실행

        return documents -> {

            // 1. 현재 들어온 총 문서 조각(Chunk)의 개수가 몇개인지 콘솔에 명확하게 표기
            System.out.println("==== 저장할 문서 조각 개수가: " + documents.size() + "=======");


            // 예쁜 제이슨 형태로 뽑을수있데
            // 들여쓰기와 줄바꿈 적용된 예쁜 JSON 문자열
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(documents);
            System.out.println(jsonString);
        };
    }


    /**
     * 서버 시작 시 RAG 데이터 적재를 수행하는 ETL 파이프라인.
     * Extract: Reader로 파일 읽기, Transform: 문서 분할/키워드 처리, Load: VectorStore와 콘솔 Writer에 저장한다.
     */
    @Bean
    public DocumentReader conversationFlowCaseReader(
            @Value("${app.rag.flow-cases-location}") Resource flowCasesResource,
            ObjectMapper objectMapper
    ) {
        return new ConversationFlowCaseReader(flowCasesResource, objectMapper);
    }

    @Bean
    public DocumentWriter vectorStoreDocumentWriter(VectorStore vectorStore) {
        return vectorStore::add;
    }

    @ConditionalOnProperty(prefix = "app.etl.pipeline", name = "init", havingValue = "true")
    @Order(1)
    @Bean
    public ApplicationRunner initEtlPipeline(
            List<DocumentReader> documentReaders,
            List<DocumentWriter> documentWriters
    ) {
        return args -> {
            System.out.println("[System] RAG ETL 파이프라인 가동 시작");

            for (DocumentReader reader : documentReaders) {
                List<Document> documents = reader.get();
                System.out.println("[Extract] RAG 문서 읽기 완료: " + documents.size() + "개");

                for (DocumentWriter writer : documentWriters) {
                    writer.accept(documents);
                }

                System.out.println("[Load] RAG 문서 저장 완료");
            }

            System.out.println("[System] RAG ETL 파이프라인 종료");
        };
    }

    /**
     * ChatClient에 붙일 RetrievalAugmentationAdvisor를 만든다.
     * 사용자의 질문이 들어오면 VectorStore에서 유사 사례를 검색하고, 그 결과를 LLM 프롬프트 context로 붙인다.
     */
    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            VectorStore vectorStore,
            ChatClient.Builder chatClientBuilder,
            @Autowired(required = false) DocumentPostProcessor documentPostProcessor)
    {

        //1. 문서 검색기 도구
        //  VectorDB에서 유사도가 충분히 가까운 문서를 최대 2개 찾아오도록 세팅
        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.45)
                .topK(2)
                .build();


        //2. 프롬프트 결합기 도구
        // 검색된 문서가 하나도 없더라도 에러를 내지말고 LLM에게 유연하게 넘기도록 세팅
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

        // 3. 최종 합체 (RetrievalAugmentationAdvisor 빌더에서 후처리기 등록)
        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter);

        // 후처리기가 존재한다면 advisor 빌더에 추가
        if (documentPostProcessor != null) {
            builder.documentPostProcessors(documentPostProcessor);
        }

        return builder.build();
    }


    @Bean
    public DocumentPostProcessor ragReferenceLogger() { //bean으로 자동등록
        return (query, documents) -> {
            System.out.println("==== RAG 검색 결과 ====");
            System.out.println("검색 질문:");
            System.out.println(query.text());
            System.out.println("찾은 문서 수: " + documents.size());

            for (Document document : documents) {
                System.out.println("문서 ID: " + document.getId());
                System.out.println("유사도 점수: " + document.getScore());
                System.out.println("metadata: " + document.getMetadata());
                System.out.println("본문 일부:");
                System.out.println(preview(document.getText())); // 너무 길면  짤라주는역할을함
                System.out.println("----------------------");
            }

            return documents;
        };
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }

        if (text.length() <= 800) {
            return text;
        }

        return text.substring(0, 800);
    }






}


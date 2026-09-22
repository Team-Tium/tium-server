package com.studiorent.tium.domain.feedback.service.rag;

import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * conversation_flow_cases.json 파일을 Spring AI의 Document 목록으로 바꾸는 Reader.
 * RAG는 JSON을 직접 검색하지 못하므로, 각 대화 사례를 검색 가능한 Document로 변환해야 한다.
 */
public class ConversationFlowCaseReader implements DocumentReader {

    private final Resource resource;
    private final ObjectMapper objectMapper;

    /**
     * @param resource  feedbackCritera.json 리소스
     * @param objectMapper JSON을 Map/List 구조로 읽기 위한 Jackson ObjectMapper
     */
    public ConversationFlowCaseReader(Resource resource, ObjectMapper objectMapper) {
        this.resource = resource;
        this.objectMapper = objectMapper;
    }

    /**
     * JSON 배열을 읽어 각 case를 하나의 Document로 변환한다.
     * 여기서 반환된 Document들이 ETL 파이프라인을 통해 VectorStore에 저장된다.
     */
    @Override
    public List<Document> get() { // 실제 json 파일을 읽는 함수  -> spring AI가 이해하는 Document목록으로 바꾸는 코드
        try {
            List<Map<String, Object>> cases = objectMapper.readValue(
                    resource.getInputStream(), // json파일을 읽기 시작하는 통로
                    new TypeReference<>() {
                    }
            );

            return cases.stream()
                    .map(this::toDocument)
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(ErrorStatus.FEEDBACK_CRITERIA_FILE_READ_FAILED);      //  파일을 읽기 실패
        }
    }

    /**
     * JSON case 하나를 RAG 검색용 Document 하나로 바꾼다.
     * text에는 LLM이 참고할 사례 내용을 넣고, metadata에는 필터링에 필요한 값을 넣는다.
     *  conversation_flow_cases.json 안의 사례 1개
     *       → VectorDB에 넣을 수 있는 Document 1개
     *
     */
    private Document toDocument(Map<String, Object> standard) {
        String standardId = stringValue(standard, "id"); // 제이슨 사례 하나의 id부분의 값을 string으로 만듦, 문서 고유이름



        /*
          지금 인자로 받은것은 원본 제이슨 파일이고
        * 작업을 하는이유  검색 필터용으로 따로 뽑아낸 작은 정보상자임
        * 검색 할때 필요한  라벨만넣는것임
        */

        Map<String, Object> metadata = new HashMap<>(); // 비어있는 metadata임 간단히 말하면 metadata은 필터용이라생각하셈
        //  -> 등록하는이유 엉뚱한 곳에서 데이터를 찾음, 정확도 극대화 함
        metadata.put("type", "conversation_standard");  // 이걸 넣으면 RAG를 검색할때 어떤 데이터인지 파악가능, 문서의 종류를 파악하는곳
        putIfPresent(metadata, "standard_id", standardId);  // 이함수는 비어있으면 넣지말고 있으면 넣으라는 함수
        putIfPresent(metadata, "relationship", standard.get("relationship"));
        putIfPresent(metadata, "category", standard.get("category"));
        putIfPresent(metadata, "source", standard.get("source"));



        String content = new StringJoiner(System.lineSeparator()) // 임베딩,유사도 검색에 쓰이는 본문
                .add("relationship: " + stringValue(standard, "relationship"))
                .add("category: " + stringValue(standard, "category"))
                .add("title: " + stringValue(standard, "title"))
                .add("principle: " + stringValue(standard, "principle"))
                .add("detect_condition: " + stringValue(standard, "detect_condition"))
                .add("bad_examples:" + System.lineSeparator() + exampleLines(standard, "bad_examples"))
                .add("good_examples:" + System.lineSeparator() + exampleLines(standard, "good_examples"))
                .add("feedback_template: " + stringValue(standard, "feedback_template"))
                .toString();

        return new Document("conversation-standard-" + standardId, content, metadata);
    }

    /**
     * metadata에 빈 값이 들어가지 않도록 값이 있을 때만 추가한다.
     */
    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !Objects.toString(value).isBlank()) {
            metadata.put(key, value);
        }
    }

    /**
     * JSON 필드 값을 문자열로 안전하게 꺼낸다.
     */
    private static String stringValue(Map<String, Object> item, String key) {
        return Objects.toString(item.get(key), "");
    }


    private static String rewritePatternLines(Map<String, Object> standard, String key) {
        Object value = standard.get(key);

        if (!(value instanceof List<?> patterns)) {
            return "";
        }

        return patterns.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(System.lineSeparator()));
    }




    private static String exampleLines(Map<String, Object> standard, String key) {
        Object value = standard.get(key);

        if (!(value instanceof List<?> examples)) {
            return "";
        }

        List<String> lines = new ArrayList<>();

        for (Object example : examples) {
            if (!(example instanceof Map<?, ?> exampleMap)) {
                continue;
            }

            Object me = exampleMap.get("me");
            Object other = exampleMap.get("other");
            Object meNext = exampleMap.get("me_next");

            if (me != null) {
                lines.add("me: " + me);
            }

            if (other != null) {
                lines.add("other: " + other);
            }

            if (meNext != null) {
                lines.add("me_next: " + meNext);
            }
        }

        return String.join(System.lineSeparator(), lines);
    }

}

package org.springframework.ai.openai.acme;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.loader.impl.JsonLoader;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.openai.testutils.AbstractIntegrationTest;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.ai.retriever.impl.VectorStoreRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.impl.InMemoryVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AcmeIntegrationTest extends AbstractIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(AcmeIntegrationTest.class);

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	@Value("classpath:/prompts/acme/system-qa.st")
	private Resource systemBikePrompt;

	@Autowired
	private OpenAiEmbeddingClient embeddingClient;

	@Autowired
	private AiClient aiClient;

	@Test
	void beanTest() {
		assertThat(bikesResource).isNotNull();
		assertThat(embeddingClient).isNotNull();
		assertThat(aiClient).isNotNull();
	}

	// @Test
	void acmeChain() {

		// Step 1 - load documents
		JsonLoader jsonLoader = new JsonLoader(bikesResource, "name", "price", "shortDescription", "description");
		List<Document> documents = jsonLoader.load();

		// Step 2 - Create embeddings and save to vector store

		logger.info("Creating Embeddings...");
		VectorStore vectorStore = new InMemoryVectorStore(embeddingClient);

		vectorStore.add(documents);

		// Now user query

		// This will be wrapped up in a chain
		VectorStoreRetriever vectorStoreRetriever = new VectorStoreRetriever(vectorStore);

		logger.info("Retrieving relevant documents");
		String userQuery = "What bike is good for city commuting?";

		// "Tell me more about the bike 'The SonicRide 8S'" ;
		// "How much does the SonicRide 8S cost?";

		// Eventually include metadata in query.
		List<Document> similarDocuments = vectorStoreRetriever.retrieve(userQuery);
		logger.info(String.format("Found %s relevant documents.", similarDocuments.size()));

		// Try the case where not product was specified, so query over whatever docs might
		// be releveant.

		Message systemMessage = getSystemMessage(similarDocuments);
		UserMessage userMessage = new UserMessage(userQuery);

		// Create the prompt ad-hoc for now, need to put in system message and user
		// message via ChatPromptTemplate or some other message building mechanic;
		logger.info("Asking AI model to reply to question.");
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		logger.info("AI responded.");
		AiResponse response = aiClient.generate(prompt);

		evaluateQuestionAndAnswer(userQuery, response, true);

		// Chain
		// qa = new ConversationalRetrievalChain(llmClient, userPromptTemplate,
		// vectorStoreRetriever, )
	}

	private Message getSystemMessage(List<Document> similarDocuments) {

		String documents = similarDocuments.stream().map(entry -> entry.getContent()).collect(Collectors.joining("\n"));

		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBikePrompt);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("documents", documents));
		return systemMessage;

	}

}

package com.aws.xray.demo.hitcounter.dynamodb;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.handlers.TracingHandler;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HitRecorder {
	private static final String TABLE_NAME = "site_hits";
	private static final String KEY = "url";
	private static final String HITS = "hits";

	private final AmazonDynamoDB CLIENT = AmazonDynamoDBClientBuilder.standard()
			.withRequestHandlers(new TracingHandler(AWSXRay.getGlobalRecorder()))
			.build();

	public void saveHit(String url) {
		try {
			UpdateItemRequest request = new UpdateItemRequest();
			request.setTableName(TABLE_NAME);
			AttributeValue keyValue = new AttributeValue().withS(url);
			request.setKey(Map.of(KEY, keyValue));
			request.setExpressionAttributeNames(Map.of("#hits", HITS));

			AttributeValue incrValue = new AttributeValue().withN("1");
			request.setExpressionAttributeValues(Map.of(":increase", incrValue));
			request.setUpdateExpression("SET #hits = #hits + :increase");
			request.setReturnValues("UPDATED_NEW");

			AWSXRay.getCurrentSegment()
					.setAnnotations(Map
							.of(KEY, url, HITS, CLIENT.updateItem(request).getAttributes().get("hits").getN()));
		}
		catch (Exception e) {
			log.error("Error while recording hit {}", e.getMessage(), e);
			AWSXRay.getCurrentSegment().addException(e);
			e.printStackTrace();
		}
	}

	public Throwable createHitEntry(String url) {
		try {
			PutItemRequest putItemRequest = new PutItemRequest();
			putItemRequest.withTableName(TABLE_NAME);
			Map<String, AttributeValue> itemMap = new HashMap<>();
			itemMap.put(KEY, new AttributeValue().withS(url));
			itemMap.put(HITS, new AttributeValue().withN("0"));
			putItemRequest.withItem(itemMap);
			CLIENT.putItem(putItemRequest);
		}
		catch (Exception e) {
			log.error("Error while creating record {} - {}", url, e.getMessage(), e);
			AWSXRay.getCurrentSegment().addException(e);
			e.printStackTrace();
			return e;
		}

		return null;
	}

	public Integer getHitCount(String url) {
		try {
			AttributeValue keyValue = new AttributeValue().withS(url);
			GetItemRequest request = new GetItemRequest().withKey(Map.of(KEY, keyValue))
					.withTableName(TABLE_NAME);
			Map<String, AttributeValue> returnedItems = CLIENT.getItem(request).getItem();
			if (returnedItems != null) {
				final Integer hitCount = Integer.valueOf(returnedItems.get(HITS).getN());
				AWSXRay.getCurrentSegment().setAnnotations(Map.of(KEY, url, "hitCount", hitCount));
				return hitCount;
			}
		}
		catch (Exception e) {
			log.error("Error while getting hit count {} - {}", url, e.getMessage(), e);
			AWSXRay.getCurrentSegment().addException(e);
			e.printStackTrace();
		}

		return 0;
	}

	@PostConstruct
	public void createTableIfNotExists() {
		try {
			AWSXRay.beginSubsegment("Bootstrap dynamodb");
			CLIENT.describeTable(TABLE_NAME).getTable();
			AWSXRay.endSubsegment();
		}
		catch (ResourceNotFoundException e) {
			CreateTableRequest request = new CreateTableRequest()
					.withAttributeDefinitions(new AttributeDefinition(KEY, ScalarAttributeType.S))
					.withKeySchema(new KeySchemaElement(KEY, KeyType.HASH))
					.withBillingMode(BillingMode.PAY_PER_REQUEST)
					.withTableName(TABLE_NAME);
			CLIENT.createTable(request);
		}
		catch (Exception e) {
			log.error("Error while creating table {} - {}", TABLE_NAME, e.getMessage(), e);
			AWSXRay.getCurrentSegment().addException(e);
			e.printStackTrace();
		}
	}
}

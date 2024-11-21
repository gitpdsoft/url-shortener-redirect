package org.urlshortnerredirect;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private S3Client s3Client = S3Client.builder().build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String pathParameter = (String) input.get("rawPath");
        String urlCode = pathParameter.replace("/", "");

        if (urlCode.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: urlCode is required.");
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket("url-shortener-npd")
                .key(urlCode + ".json")
                .build();

        InputStream s3ObjectStream;
        try {
            s3ObjectStream = s3Client.getObject(getObjectRequest);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching URL data from S3.", e);
        }

        UrlData urlData;
        try {
            urlData = objectMapper.readValue(s3ObjectStream, UrlData.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON from S3.", e);
        }
        long currentTimeInSeconds = System.currentTimeMillis() / 1000L;
        Map<String, Object> response = new HashMap<>();

        if (currentTimeInSeconds > urlData.getExpirationTime()) {
            response.put("statusCode", 410);
            response.put("body", "This URL has expired");
        }

        response.put("statusCode", 302);
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", urlData.getFromUrl());
        response.put("headers", headers);
        return response;
    }
}
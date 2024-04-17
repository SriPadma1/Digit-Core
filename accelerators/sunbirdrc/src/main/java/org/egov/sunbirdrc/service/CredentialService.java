package org.egov.sunbirdrc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.egov.sunbirdrc.kafka.Producer;
import org.egov.sunbirdrc.models.CredentialIdUuidMapper;
import org.egov.sunbirdrc.repository.CredentialUuidRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@Getter
@Setter
public class CredentialService {


    @Value("${sunbird.credential.url}")
    private String fetchCredentialUrl;

    @Value("${sunbird.save.vc.topic}")
    private String saveVcidTopic;

    @Autowired
    private MdmsSchemaService mdmsSchemaService;

    @Autowired
    private Producer producer;

    @Autowired
    private CredentialIdUuidMapper credentialIdUuidMapper;


    @Autowired
    private CredentialUuidRepository credentialUuidRepository;

    @Autowired
    private RevokeCredentialService revokeCredentialService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;



    public void processPayloadAndPersistCredential(String entityRequestPayload,String topic) {
        try{

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode requestPayload = objectMapper.readTree(entityRequestPayload);
            String entityModuleName = requestPayload.path("module").asText();

            JsonNode mdmsModuleObject = mdmsSchemaService.getModuleDetailsFromMdmsData(entityModuleName);

            //get did, schemaId, uuid and jsonpath details from the mdms object
            String entityDid= getDidFromModuleObject(mdmsModuleObject);
            String entitySchemaId=getSchemaIdFromModuleObject(mdmsModuleObject);
            String uuid=getUuidFromModuleObject(mdmsModuleObject);
            JsonNode listofJsonPaths= getAllFieldPathFromMdms(mdmsModuleObject);

            JsonNode payloadFromJsonPath= extractPayloadFromJsonPath(listofJsonPaths,requestPayload,entityDid);
            //check condition for create-vc and recreate-vc
            if (topic.equals("recreate-vc")){
                CredentialIdUuidMapper credentialUuidObject=credentialUuidRepository.getUuidVcidMapperRow(uuid);
                String revokeApiResponse=revokeCredentialService.revokeCredential(credentialUuidObject.getVcid());
                JsonNode jsonNode = objectMapper.readTree(revokeApiResponse);
                String status = jsonNode.get("status").asText();
                if(status.equals("REVOKED")){
                    String credentialIdUuidData=generateCredentials(uuid, entityDid, entitySchemaId,payloadFromJsonPath);
                    if(credentialIdUuidData!=null){
                        producer.push("update-vcid", credentialIdUuidData);
                    }
                }
                else{
                    log.error("verifiable credentials for the entity is not revoked");
                }
            }
            else{
                String credentialIdUuidData=generateCredentials(uuid, entityDid, entitySchemaId,payloadFromJsonPath);
                if (credentialIdUuidData!=null){
                    producer.push(saveVcidTopic, credentialIdUuidData);
                }
            }
            //update cache after db update
            credentialUuidRepository.invalidateCache("vcid_uuid_mapper","vc-mdms");
            credentialUuidRepository.loadData();
        }catch (JsonProcessingException e) {
            log.error("Exception occurred while processing JSON: " + e.getMessage());
        }
    }

    public String getDidFromModuleObject(JsonNode mdmsModuleObject) {
        return getIdDetails(mdmsModuleObject, "did");
    }

    public String getSchemaIdFromModuleObject(JsonNode mdmsModuleObject) {
        return getIdDetails(mdmsModuleObject, "schemaId");
    }

    public String getUuidFromModuleObject(JsonNode mdmsModuleObject) {
        return getIdDetails(mdmsModuleObject, "uuid");
    }

    public String getIdDetails(JsonNode mdmsModuleObject, String fieldName) {
        if (mdmsModuleObject != null && mdmsModuleObject.has("definition")) {
            JsonNode definitionNode = mdmsModuleObject.get("definition");
            if (definitionNode != null && definitionNode.has(fieldName)) {
                return definitionNode.get(fieldName).asText();
            }
        }
        return null;
    }

    public JsonNode getAllFieldPathFromMdms(JsonNode mdmsModuleObject){
        if (mdmsModuleObject != null && mdmsModuleObject.has("definition")) {
            JsonNode definitionNode = mdmsModuleObject.get("definition");
            if (definitionNode != null && definitionNode.has("path")) {
                JsonNode fieldJsonPathList = definitionNode.get("path");
                return fieldJsonPathList;
            }
        }
        return null;
    }

    public JsonNode extractPayloadFromJsonPath(JsonNode listofJsonPaths, JsonNode requestPayload,String did){
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode credentialPayloadData = objectMapper.createObjectNode();

        listofJsonPaths.forEach(pathNode -> {
            String fieldPath = pathNode.asText();
            try {
                Object fieldValue = JsonPath.read(requestPayload.toString(), fieldPath);
                String lastKey = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
                if (fieldValue != null) {
                    credentialPayloadData.put(lastKey, fieldValue.toString());
                } else {
                    credentialPayloadData.putNull(lastKey); // Or handle missing values differently
                }
            } catch (PathNotFoundException e) {
                // Path not found in requestPayload
                log.error("json path not found in the payload",e);
                String lastKey = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
                credentialPayloadData.putNull(lastKey);
            }
        });
        //adding id field which is mandatory and can be random id for sunbird api
        credentialPayloadData.put("id", did);
        return credentialPayloadData;
    }




   public String generateCredentials(String uuid, String did, String schemaId,JsonNode credentialPayload) {
       if (did == null || schemaId == null || uuid == null) {
           throw new IllegalArgumentException("Did, schemaId and uuid cannot be null");
       }
        ObjectMapper objectMapper = new ObjectMapper();
        HttpHeaders headers = new HttpHeaders();
        try{
            headers.setContentType(MediaType.APPLICATION_JSON);

            String credentialRequestPayload = objectMapper.writeValueAsString(credentialPayload);
            // Define the request body
            String requestBody = "{\n" +
                    "    \"credential\": {\n" +
                    "        \"@context\": [\n" +
                    "            \"https://www.w3.org/2018/credentials/v1\",\n" +
                    "            \"https://schema.org\"\n" +
                    "        ],\n" +
                    "        \"id\": \"" + did + "\",\n" +
                    "        \"type\": [\n" +
                    "            \"VerifiableCredential\",\n" +
                    "            \"UniversityDegreeCredential\"\n" +
                    "        ],\n" +
                    "        \"issuer\": \"" + did + "\",\n" +
                    "        \"expirationDate\": \"2023-02-08T11:56:27.259Z\",\n" +
                    "        \"credentialSubject\": " + credentialRequestPayload + "\n" +
                    "    },\n" +
                    "    \"credentialSchemaId\": \"" + schemaId + "\",\n" +
                    "    \"credentialSchemaVersion\": \"1.0.0\"\n" +
                    "}";

            // Create the request entity
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
            // Make the HTTP POST request
            ResponseEntity<String> response = restTemplate.exchange(fetchCredentialUrl, HttpMethod.POST, requestEntity, String.class);
            credentialIdUuidMapper.setVcid(getIdFromResponse(response));

            credentialIdUuidMapper.setUuid(uuid);
            credentialIdUuidMapper.setCreatedBy(did);
            return objectMapper.writeValueAsString(credentialIdUuidMapper);
        }
        catch (JsonProcessingException e) {
           log.error("Exception occurred while processing JSON: " + e.getMessage());
           return null;
       }

    }

    public String getCredential(String entityId){
        CredentialIdUuidMapper credentialUuidObject=credentialUuidRepository.getUuidVcidMapperRow(entityId);
        return credentialUuidObject.getVcid();
    }


    private String getIdFromResponse(ResponseEntity<String> response) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(response.getBody());
        JsonNode credentialNode = rootNode.path("credential");
        return credentialNode.path("id").asText();
    }
}
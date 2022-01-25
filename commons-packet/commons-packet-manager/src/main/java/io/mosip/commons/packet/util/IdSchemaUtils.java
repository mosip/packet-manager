/**
 *
 */
package io.mosip.commons.packet.util;

import static io.mosip.commons.packet.constants.PacketManagerConstants.FIELDCATEGORY;
import static io.mosip.commons.packet.constants.PacketManagerConstants.IDENTITY;
import static io.mosip.commons.packet.constants.PacketManagerConstants.PROPERTIES;
import static io.mosip.commons.packet.constants.PacketManagerConstants.RESPONSE;
import static io.mosip.commons.packet.constants.PacketManagerConstants.SCHEMA_JSON;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.exception.ApiNotAccessibleException;

/**
 * The Class IdSchemaUtils.
 */

/**
 * Instantiates a new id schema utils.
 */
@Component
public class IdSchemaUtils {

    private org.json.simple.JSONObject mappingJsonObject = null;
    private static Map<String, String> categorySubpacketMapping = new HashMap<>();
    private Map<Double, String> idschema = null;
    public static final String RESPONSE = "response";
    public static final String PROPERTIES = "properties";
    public static final String IDENTITY = "identity";
    public static final String SCHEMA_CATEGORY = "fieldCategory";
    public static final String SCHEMA_ID = "id";
    public static final String SCHEMA_TYPE = "type";
    public static final String SCHEMA_REF = "$ref";
    public static final String IDSCHEMA_URL = "IDSCHEMA";
    public static final String SCHEMA_JSON = "schemaJson";
    public static final String SCHEMA_VERSION_QUERY_PARAM = "schemaVersion";
    public static final String SCHEMA_REF_DEFINITIONS_PREFIX = "#/definitions/";

    static {
        categorySubpacketMapping.put("pvt", "id");
        categorySubpacketMapping.put("kyc", "id");
        categorySubpacketMapping.put("none", "id,evidence,optional");
        categorySubpacketMapping.put("evidence", "evidence");
        categorySubpacketMapping.put("optional", "optional");
    }

    @Value("${config.server.file.storage.uri}")
    private String configServerUrl;

    @Value("${registration.processor.identityjson}")
    private String mappingjsonFileName;

    @Value("${packet.default.source:REGISTRATION_CLIENT}")
    private String defaultSource;

    @Value("${schema.default.fieldCategory:pvt,none}")
    private String defaultFieldCategory;

    @Value("${IDSCHEMAURL:null}")
    private String idschemaUrl;
    
    @Autowired
    private ObjectMapper objMapper;

    @Autowired
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;


    /**
     * Gets the source field category from id schema
     *
     * @param fieldName       the field name in schema
     * @param idschemaVersion : the idschema version used to create packet
     * @return the source
     * @throws IOException
     */
    public String getSource(String fieldName, Double idschemaVersion) throws IOException, ApiNotAccessibleException {
        String idSchema = getIdSchema(idschemaVersion);
        JSONObject properties = getJSONObjFromStr(idSchema, PROPERTIES);
        JSONObject identity = getJSONObj(properties, IDENTITY);
        JSONObject property = getJSONObj(identity, PROPERTIES);
        JSONObject value = getJSONObj(property, fieldName);
        String fieldCategory = getFieldCategory(value);
        return fieldCategory;
    }

    /**
     * Get the id schema from syncdata service
     *
     * @return idschema as string
     * @throws ApiNotAccessibleException
     * @throws IOException
     */
    public String getIdSchema(Double version) throws ApiNotAccessibleException, IOException {
        if (idschema != null && !idschema.isEmpty() && idschema.get(version) != null)
            return idschema.get(version);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idschemaUrl);
        if (version != null)
            builder.queryParam(PacketManagerConstants.SCHEMA_VERSION_QUERY_PARAM, version);
        UriComponents uriComponents = builder.build(false).encode();

        String response = restTemplate.getForObject(uriComponents.toUri(), String.class);
        String responseString = null;
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONObject respObj = (JSONObject) jsonObject.get(RESPONSE);
            responseString = respObj != null ? (String) respObj.get(SCHEMA_JSON) : null;
        } catch (JSONException e) {
            throw new IOException(e);
        }

        if (responseString != null) {
            if (idschema == null) {
                idschema = new HashMap<>();
                idschema.put(version, responseString);
            } else
                idschema.put(version, responseString);
        } else
            throw new ApiNotAccessibleException("Could not get id schema");

        return idschema.get(version);
    }

    /**
     * Gets the field category.
     *
     * @param jsonObject the json object
     * @return the field category
     */
    private String getFieldCategory(JSONObject jsonObject) {
        String fieldCategory = null;
        try {
            fieldCategory = jsonObject != null ? jsonObject.getString(FIELDCATEGORY) : null;
        } catch (JSONException e) {
            fieldCategory = null;
        }
        String[] defaultCategories = defaultFieldCategory != null ? defaultFieldCategory.split(",") : null;
        if (fieldCategory != null && defaultCategories != null
                && ArrayUtils.contains(defaultCategories, fieldCategory)) {
            fieldCategory = defaultSource;
        }
        return fieldCategory;
    }

    /**
     * Search a field in json
     *
     * @param jsonObject
     * @param id
     * @return
     */
    private JSONObject getJSONObj(JSONObject jsonObject, String id) {
        try {
            return (jsonObject == null) ? null : (JSONObject) jsonObject.get(id);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Search a field in json string
     *
     * @param jsonString
     * @param id
     * @return
     */
    private JSONObject getJSONObjFromStr(String jsonString, String id) {
        try {
            return (jsonString == null) ? null : (JSONObject) new JSONObject(jsonString).get(id);
        } catch (JSONException e) {
            return null;
        }
    }

    public List<String> getDefaultFields(Double schemaVersion) throws JSONException, IOException {
        List<String> fieldList = new ArrayList<>();
        List<Map<String, String>> fieldMapList = loadDefaultFields(schemaVersion);
        fieldMapList.stream().forEach(f -> fieldList.add(f.get(SCHEMA_ID)));
        return fieldList;
    }

    public List<Map<String, String>> loadDefaultFields(Double schemaVersion) throws JSONException, IOException {
        Map<String, List<Map<String, String>>> packetBasedMap = new HashMap<String, List<Map<String, String>>>();

        String schemaJson = getIdSchema(schemaVersion);

        JSONObject schema = getIdentityFieldsSchema(schemaJson);

        JSONArray fieldNames = schema.names();
        for(int i=0;i<fieldNames.length();i++) {
            String fieldName = fieldNames.getString(i);
            JSONObject fieldDetail = schema.getJSONObject(fieldName);
            String fieldCategory = fieldDetail.has(SCHEMA_CATEGORY) ?
                    fieldDetail.getString(SCHEMA_CATEGORY) : "none";
            String packets = categorySubpacketMapping.get(fieldCategory.toLowerCase());

            String[] packetNames = packets.split(",");
            for(String packetName : packetNames) {
                if(!packetBasedMap.containsKey(packetName)) {
                    packetBasedMap.put(packetName, new ArrayList<Map<String, String>>());
                }

                Map<String, String> attributes = new HashMap<>();
                attributes.put(SCHEMA_ID, fieldName);
                attributes.put(SCHEMA_TYPE, fieldDetail.has(SCHEMA_REF) ?
                        fieldDetail.getString(SCHEMA_REF) : fieldDetail.getString(SCHEMA_TYPE));
                packetBasedMap.get(packetName).add(attributes);
            }
        }
        return packetBasedMap.get("id");
    }

    private JSONObject getIdentityFieldsSchema(String schemaJson) throws JSONException {

        JSONObject schema = new JSONObject(schemaJson);
        schema =  schema.getJSONObject(PROPERTIES);
        schema =  schema.getJSONObject(IDENTITY);
        schema =  schema.getJSONObject(PROPERTIES);

        return schema;
    }


    public String getIdschemaVersionFromMappingJson() throws IOException {
        String field = getJSONValue(getJSONObject(getMappingJson(), PacketManagerConstants.IDSCHEMA_VERSION), PacketManagerConstants.VALUE);
        return field;

    }

    public org.json.simple.JSONObject getMappingJson() throws IOException {

        if (mappingJsonObject == null) {
            String mappingJsonString = restTemplate.getForObject(configServerUrl + "/" + mappingjsonFileName, String.class);
            mappingJsonObject = objMapper.readValue(mappingJsonString, org.json.simple.JSONObject.class);

        }
        return getJSONObject(mappingJsonObject, PacketManagerConstants.IDENTITY);
    }

    public static org.json.simple.JSONObject getJSONObject(org.json.simple.JSONObject jsonObject, Object key) {
        if(jsonObject == null)
            return null;
        LinkedHashMap identity = (LinkedHashMap) jsonObject.get(key);
        return identity != null ? new org.json.simple.JSONObject(identity) : null;
    }

    public static <T> T getJSONValue(org.json.simple.JSONObject jsonObject, String key) {
        if(jsonObject == null)
            return null;
        T value = (T) jsonObject.get(key);
        return value;
    }
}

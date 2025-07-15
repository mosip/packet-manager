/**
 *
 */
package io.mosip.commons.packet.util;

import static io.mosip.commons.packet.constants.PacketManagerConstants.FIELDCATEGORY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

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

	private static Map<String, String> categorySubpacketMapping = new HashMap<>();
	private static final Map<Double, String> idSchemaCache = new ConcurrentHashMap<>();

	private org.json.simple.JSONObject mappingJsonObject = null;
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
		JSONObject fieldObj;
		try {
			fieldObj = getFieldFromSchema(idSchema, fieldName);
		} catch (JSONException e) {
			throw new IOException(e);
		}
		return resolveFieldCategory(fieldObj);
	}

	/**
	 * Get the id schema from syncdata service
	 *
	 * @return idschema as string
	 * @throws ApiNotAccessibleException
	 * @throws IOException
	 */
	public String getIdSchema(Double version) throws ApiNotAccessibleException, IOException {
		if (idschema == null) {
			idschema = new HashMap<>();
		}

		// Check cache first
		if (idschema.containsKey(version)) {
			return idschema.get(version);
		}

		// Build URI with query param
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(idschemaUrl);
		if (version != null) {
			builder.queryParam(PacketManagerConstants.SCHEMA_VERSION_QUERY_PARAM, version);
		}
		UriComponents uriComponents = builder.build(false).encode();

		// Make REST call
		String response;
		try {
			response = restTemplate.getForObject(uriComponents.toUri(), String.class);
		} catch (Exception e) {
			throw new ApiNotAccessibleException("Failed to fetch ID schema from URL: " + uriComponents.toUri(), e);
		}

		// Parse JSON
		String responseString;
		try {
			JSONObject jsonObject = new JSONObject(response);
			JSONObject respObj = jsonObject.optJSONObject(RESPONSE);
			responseString = respObj != null ? respObj.optString(SCHEMA_JSON, null) : null;
		} catch (JSONException e) {
			throw new IOException("Invalid JSON received from ID Schema service", e);
		}

		// Final validation and cache
		if (responseString == null) {
			throw new ApiNotAccessibleException("Could not get ID schema for version: " + version);
		}

		idschema.put(version, responseString);
		return responseString;
	}

	private JSONObject getFieldFromSchema(String schemaJson, String fieldName) throws JSONException {
		JSONObject schema = new JSONObject(schemaJson).getJSONObject(PROPERTIES).getJSONObject(IDENTITY)
				.getJSONObject(PROPERTIES);
		return schema.getJSONObject(fieldName);
	}

	private String resolveFieldCategory(JSONObject jsonObject) {
		String category = jsonObject.optString(FIELDCATEGORY, null);
		if (category != null && defaultFieldCategory != null
				&& ArrayUtils.contains(defaultFieldCategory.split(","), category)) {
			return defaultSource;
		}
		return category;
	}

	public List<String> getDefaultFields(Double schemaVersion) throws JSONException, IOException {
		List<Map<String, String>> defaultFieldMaps = loadDefaultFields(schemaVersion);
		List<String> fieldIds = new ArrayList<>(defaultFieldMaps.size());
		defaultFieldMaps.forEach(map -> fieldIds.add(map.get(SCHEMA_ID)));
		return fieldIds;
	}

	public List<Map<String, String>> loadDefaultFields(Double schemaVersion) throws JSONException, IOException {
		JSONObject identitySchema = getIdentityFieldsSchema(getIdSchema(schemaVersion));
		Map<String, List<Map<String, String>>> packetBasedMap = new HashMap<>();

		Iterator<String> fieldNames = identitySchema.keys();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			JSONObject fieldDetail = identitySchema.getJSONObject(fieldName);
			String category = fieldDetail.optString(SCHEMA_CATEGORY, "none");
			String[] packets = categorySubpacketMapping.getOrDefault(category.toLowerCase(), "id").split(",");

			for (String packet : packets) {
				packetBasedMap.computeIfAbsent(packet, k -> new ArrayList<>());
				Map<String, String> attr = new HashMap<>();
				attr.put(SCHEMA_ID, fieldName);
				attr.put(SCHEMA_TYPE, fieldDetail.has(SCHEMA_REF) ? fieldDetail.getString(SCHEMA_REF)
						: fieldDetail.optString(SCHEMA_TYPE, "string")); // Default to "string" if missing
				packetBasedMap.get(packet).add(attr);
			}
		}

		return packetBasedMap.getOrDefault("id", Collections.emptyList());
	}

	private JSONObject getIdentityFieldsSchema(String schemaJson) throws JSONException {
		return new JSONObject(schemaJson).getJSONObject(PROPERTIES).getJSONObject(IDENTITY).getJSONObject(PROPERTIES);
	}

	public String getIdschemaVersionFromMappingJson() throws IOException {
		return getJSONValue(getJSONObject(getMappingJson(), PacketManagerConstants.IDSCHEMA_VERSION),
				PacketManagerConstants.VALUE);
	}

	public org.json.simple.JSONObject getMappingJson() throws IOException {
		if (mappingJsonObject == null) {
			String mappingJsonString = restTemplate.getForObject(configServerUrl + "/" + mappingjsonFileName,
					String.class);
			mappingJsonObject = objMapper.readValue(mappingJsonString, org.json.simple.JSONObject.class);
		}
		return getJSONObject(mappingJsonObject, IDENTITY);
	}

	public static org.json.simple.JSONObject getJSONObject(org.json.simple.JSONObject jsonObject, Object key) {
		if (jsonObject == null)
			return null;
		LinkedHashMap<?, ?> map = (LinkedHashMap<?, ?>) jsonObject.get(key);
		return map != null ? new org.json.simple.JSONObject(map) : null;
	}

	public static <T> T getJSONValue(org.json.simple.JSONObject jsonObject, String key) {
		return (jsonObject != null) ? (T) jsonObject.get(key) : null;
	}
}
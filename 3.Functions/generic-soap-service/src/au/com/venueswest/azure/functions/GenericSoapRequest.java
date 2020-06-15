package au.com.venueswest.azure.functions;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.activation.DataHandler;
import javax.xml.ws.Binding;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.oracle.ucm.Field;
import com.oracle.ucm.File;
import com.oracle.ucm.Generic;
import com.oracle.ucm.ResultSet;
import com.oracle.ucm.Row;
import com.oracle.ucm.Service;

import genericsoap.GenericSoapPortType;
import genericsoap.GenericSoapService;

public class GenericSoapRequest {
	@FunctionName("GenericSoapRequest")
	public HttpResponseMessage run(@HttpTrigger(name = "req", methods = {
			HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {
		context.getLogger().info("Executing Get File");
		
		final String docId = request.getQueryParameters().get("documentId");

		context.getLogger().info("Decoding body");
//		if (!request.getBody().isPresent()) {
//			JSONObject jsonObjects = new JSONObject();
//			jsonObjects.put("status", "failed");
//			jsonObjects.put("message", "No body present");
//			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("XXX" + jsonObjects.toString()).build();
//		}
		
//		String contentType = request.getHeaders().get("Content-Type");
//		
//		if (!contentType.equalsIgnoreCase("application/json")) {
//			JSONObject jsonObjects = new JSONObject();
//			jsonObjects.put("status", "failed");
//			jsonObjects.put("message", "Invalid Content-Type, application/json");
//			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonObjects.toString()).build();
//		}
		
		String body = request.getBody().get();
//		return request.createResponseBuilder(HttpStatus.OK).body(body);
		
		JSONObject jsonObjects = new JSONObject(body);
//		context.getLogger().info("Method Type XXX");
//		context.getLogger().info("Method Type: " + jsonObjects.getString("method"));
		
		GenericSoapService serviceImpl = null;
		GenericSoapPortType port = null;
		Map<String, Object> requestContext = null;
		
		Generic objRequest = null;
		
		JSONObject jsonResponse = new JSONObject();
		
		// Get Credentials
		if (jsonObjects.keySet().contains("authentication")) {
			context.getLogger().info("Authentication section found, applying authentication");
			
			JSONObject authentication = jsonObjects.getJSONObject("authentication");
			
			if (!authentication.keySet().contains("username") || !authentication.keySet().contains("password")) {
				context.getLogger().info("Missing authentication username or password");
				jsonResponse.put("status", "failed");
				jsonResponse.put("message", "Missing authentication username or password");
				return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
			}
			
			String wsdlLocation = null;
			
			if (authentication.keySet().contains("wsdlLocation")) {
				wsdlLocation = authentication.getString("wsdlLocation");
			}
			
			if (wsdlLocation == null) {
				context.getLogger().info("No wsdlLocation passed, using DEV default");
				serviceImpl = new GenericSoapService();
			}
			else {
				context.getLogger().info("wsdlLocation passed, " + wsdlLocation);
				URL endpoint;
				try {
					endpoint = new URL(wsdlLocation);
				}
				catch (MalformedURLException _exc) {
					context.getLogger().info("Endpoint Error: " + _exc.getMessage());
					jsonResponse.put("status", "failed");
					jsonResponse.put("message", "Endpoint Error: " + _exc.getMessage());
					return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
				}
				serviceImpl = new GenericSoapService(endpoint);
			}
			
			port = serviceImpl.getGenericSoapPort();
			requestContext = ((BindingProvider)port).getRequestContext();
			
			setRequestContextCredentials(requestContext, authentication.getString("username"), authentication.getString("password"));
		}
		else {
			context.getLogger().info("Missing authentication section");
			jsonResponse.put("status", "failed");
			jsonResponse.put("message", "Missing authentication section");
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
		}
		
		String webKey = "";
		
		if (jsonObjects.keySet().contains("genericRequest")) {
			context.getLogger().info("Found genericRequest");
			JSONObject genericRequest = jsonObjects.getJSONObject("genericRequest");
			
			if (genericRequest.keySet().contains("webKey")) {
				webKey = genericRequest.getString("webKey");
			}
			else {
				context.getLogger().info("Missing webKey");
				jsonResponse.put("status", "failed");
				jsonResponse.put("message", "Missing webKey");
				return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
			}
			
			if (genericRequest.keySet().contains("service")) {
				context.getLogger().info("Found service");
				JSONObject service = genericRequest.getJSONObject("service");
				
				String idcService = "";
				
				if (service.keySet().contains("idcService")) {
					context.getLogger().info("Found idcService");
					idcService = service.getString("idcService");
				}
				else {
					context.getLogger().info("Missing idcService");
					jsonResponse.put("status", "failed");
					jsonResponse.put("message", "Missing idcService");
					return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
				}
				
				objRequest = createServiceRequest(webKey, idcService);
				
				if (service.keySet().contains("document")) {
					context.getLogger().info("Found document");
					JSONObject document = service.getJSONObject("document");
					
					if (document.keySet().contains("fields")) {
						context.getLogger().info("Found fields");
						JSONArray fields = document.getJSONArray("fields");
						context.getLogger().info("Field count = " + fields.length());
						for (int i = 0 ; i < fields.length() ; i++) {
							JSONObject field = fields.getJSONObject(i);
							
							if (!field.keySet().contains("name") || !field.keySet().contains("value")) {
								context.getLogger().info("Missing name or value for field");
								
								jsonResponse.put("status", "failed");
								jsonResponse.put("message", "Field missing name or value");
								return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
							}
							
							addDocumentRequestProperty(objRequest, field.getString("name"), field.getString("value"));
						}
					}
				}
			}
			else {
				context.getLogger().info("Missing service");
				jsonResponse.put("status", "failed");
				jsonResponse.put("message", "Missing service");
				return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
			}
		}
		else {
			context.getLogger().info("Missing genericRequest");
			jsonResponse.put("status", "failed");
			jsonResponse.put("message", "Missing genericRequest");
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
		}
		
		if (objRequest == null) {
			context.getLogger().info("Generic is null");
			jsonResponse.put("status", "failed");
			jsonResponse.put("message", "Generic is null");
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
		}

		context.getLogger().info("Sending Request");
		Generic response = port.genericSoapOperation(objRequest);
		context.getLogger().info("Sent Request");
		
		if (response == null) {
			context.getLogger().info("Response is null");
			jsonResponse.put("status", "failed");
			jsonResponse.put("message", "Missing response is null");
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
		}
		
		jsonResponse.put("status", "success");
		
		JSONObject genericResponse = new JSONObject();
		jsonResponse.put("genericResponse", genericResponse);
		
		if (response.getService() == null) {
			context.getLogger().info("No service in response");
			jsonResponse.put("status", "failed");
			jsonResponse.put("message", "No service in response");
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(jsonResponse.toString()).build();
		}
		
		JSONObject serviceResponse = new JSONObject();
		genericResponse.put("service", serviceResponse);
		
		serviceResponse.put("idcService", response.getService().getIdcService());
		
		JSONObject documentResponse = new JSONObject();
		serviceResponse.put("document", documentResponse);
		
		context.getLogger().info(response.toString());
		List<Field> fields = response.getService().getDocument().getField();
		context.getLogger().info("Response Document Fields Size: " + fields.size());
		
		if (fields.size() > 0) {
			JSONArray responseFields = new JSONArray();
			for (int i = 0; i < fields.size(); i++) {
				JSONObject field = new JSONObject();
				field.put("name", fields.get(i).getName());
				field.put("value", fields.get(i).getValue());
				responseFields.put(field);
			}
			documentResponse.put("fields",  responseFields);
		}
		
		List<ResultSet> results = response.getService().getDocument().getResultSet();
		context.getLogger().info("Results Size: " + results.size());
		
		if (results.size() > 0) {
			JSONArray responseResultSets = new JSONArray();
			for (int r = 0; r < results.size(); r++) {
				ResultSet rsObj = results.get(r);
				JSONObject resultSet = new JSONObject();
				resultSet.put("name", rsObj.getName());
				
				context.getLogger().info("Response Document Result Set Rows Size: " + rsObj.getRow().size() );
				if (rsObj.getRow().size() > 0) {
					JSONArray responseResultSetRows = new JSONArray();
					
					for(int rw = 0 ; rw < rsObj.getRow().size() ; rw++) {
						JSONObject responseRow = new JSONObject();
						Row row = rsObj.getRow().get(rw);
						if (row.getField().size() > 0) {
							JSONArray responseFields = new JSONArray();
							for (int i = 0; i < row.getField().size(); i++) {
								JSONObject field = new JSONObject();
								field.put("name", row.getField().get(i).getName());
								field.put("value", row.getField().get(i).getValue());
								responseFields.put(field);
							}
							responseRow.put("fields", responseFields);
						}
						responseResultSetRows.put(responseRow);
					}
					resultSet.put("rows", responseResultSetRows);
				}
				responseResultSets.put(resultSet);
			}
			documentResponse.put("resultSets",  responseResultSets);
		}

		List<com.oracle.ucm.File> files = response.getService().getDocument().getFile();
		context.getLogger().info("Files Size: " + files.size());
		
		if (files.size() > 0) {
			JSONArray responseFiles = new JSONArray();
			for (int i = 0; i < files.size(); i++) {
				File file = files.get(i);
				
				JSONObject resFile = new JSONObject();
				resFile.put("name", file.getName());
				resFile.put("href", file.getHref());
				
				DataHandler contents = files.get(i).getContents();
				context.getLogger().info("Found File: " + files.get(i).getName() + "(" + files.get(i).getHref() + ")");
				
				String base64File = getFileContentsAsBase64(contents);
				if (base64File != null) {
//					context.getLogger().info(base64File);
					resFile.put("contents", base64File);
				}
				responseFiles.put(resFile);
			}
			documentResponse.put("files", responseFiles);
		}
		
		return request.createResponseBuilder(HttpStatus.OK).body(jsonResponse.toString()).build();
	}
	
	public static String getFileContentsAsBase64(DataHandler contents) {
		InputStream in  = null;
		try {
			in = contents.getInputStream();
			
			byte[] bytes = IOUtils.toByteArray(in);
			return Base64.getEncoder().encodeToString(bytes);
		} catch (IOException _exc) {
			_exc.printStackTrace();
		}
		return null;
	}

	public static ResultSet getDocumentResultSet(Generic response, String resultSetName) {
		ResultSet resultset = null;
		List<ResultSet> resultSets = response.getService().getDocument().getResultSet();
		for (ResultSet rs : resultSets) {
			if (rs.getName().equals(resultSetName)) {
				resultset = rs;
				break;
			}
		}
		return resultset;
	}

	public static void addDocumentRequestProperty(Generic request, String name, String value) {
		List<Field> binder = request.getService().getDocument().getField();
		Field f = new Field();
		f.setName(name);
		f.setValue(value);
		binder.add(f);
	}

	public static List<Map<String, String>> getDataObjects(ResultSet rs) {
		List<Map<String, String>> list = new ArrayList<>();
		for (Row row : rs.getRow()) {
			Map<String, String> m = new HashMap<String, String>();
			for (Field f : row.getField())
				m.put(f.getName(), f.getValue());
			list.add(m);
		}
		return list;
	}

	public Generic createServiceRequest(String webKey, String serviceName) {
		Generic request = new Generic();
		request.setWebKey(webKey);
		Service service = new Service();
		service.setIdcService(serviceName);
		request.setService(service);
		Service.Document document = new Service.Document();
		service.setDocument(document);
//		addRequestProperty(request, "UserDateFormat", "iso8601");
//		addRequestProperty(request, "UserTimeZone", "UTC");
		return request;
	}

	public static List<Map<String, String>> getDataObjects(Generic response, String resultSetName) {
		ResultSet rs = getDocumentResultSet(response, resultSetName);
		return getDataObjects(rs);
	}

	public static Map<String, String> getDataObject(Generic response, String resultSetName, int rowIndex) {
		ResultSet rs = getDocumentResultSet(response, resultSetName);
		Map<String, String> result = new HashMap<>();
		List<Row> rows = rs.getRow();
		if (rows != null && rows.size() > rowIndex && rowIndex >= 0) {
			Row row = rows.get(rowIndex);
			List<Field> rowFields = row.getField();
			for (Field f : rowFields) {
				result.put(f.getName(), f.getValue());
			}
		}
		return result;
	}

	public static void throwOnResponseError(Generic response) {
		Map<String, String> rb = getResponseLocalData(response);
		if (rb.containsKey("StatusCode") && Integer.valueOf(rb.get("StatusCode")).intValue() != 0) {
			throw new IllegalStateException("ServiceException: " + (String) rb.get("StatusCode") + " - " + (String) rb.get("StatusMessage"));
		}
	}

	public static Map<String, String> getResponseLocalData(Generic response) {
		List<Field> responseFields = response.getService().getDocument().getField();
		Map<String, String> result = new HashMap<>();
		for (Field f : responseFields) {
			result.put(f.getName(), f.getValue());
		}
		return result;
	}

	public static int getResponseCode(Generic response) {
		Map<String, String> rb = getResponseLocalData(response);
		int result = -1;
		if (rb.containsKey("StatusCode")) {
			result = Integer.valueOf(rb.get("StatusCode")).intValue();
		}
		return result;
	}
	
	private void setRequestContextCredentials(Map<String, Object> requestContext, String username, String password) {
	    requestContext.put("javax.xml.ws.security.auth.username", username);
	    requestContext.put("javax.xml.ws.security.auth.password", password);
	}
}

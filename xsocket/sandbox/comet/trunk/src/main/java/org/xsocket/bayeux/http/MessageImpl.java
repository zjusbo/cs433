package org.xsocket.bayeux.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xsocket.DataConverter;

import dojox.cometd.Bayeux;
import dojox.cometd.Message;


import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;


final class MessageImpl implements Message {

	private JSONObject jsonObject = null;
	
	private String channel = null;
	private Map<String, Object> ext = null;
	
	
	private MessageImpl(JSONObject jsonObject) {
		this.jsonObject = jsonObject;
	}
	

	
	public static MessageImpl newInstance(String channel) {
		MessageImpl message = new MessageImpl(new JSONObject());
		message.setChannel(channel);
		return message;
	}

	public static List<MessageImpl> deserialize(String serializedJSON) {
		List<MessageImpl> result = new ArrayList<MessageImpl>();
		
		if (serializedJSON.charAt(0) == '[') {
			JSONArray jsonArray = JSONArray.fromObject(serializedJSON);
			for (Object jsonObject : jsonArray) {
				MessageImpl message = new MessageImpl((JSONObject) jsonObject);
				result.add(message);
			}
		} else {
			JSONObject jsonObject = JSONObject.fromObject(serializedJSON);
			result.add(new MessageImpl((JSONObject) jsonObject));
		}
		
		return result;
	}
	
	
	public static String serialize( boolean encode, MessageImpl... messages) {
		return serialize(encode, Arrays.asList(messages));
	}
	
	
	public static String serialize(boolean encode, List<MessageImpl> messages) {
		
		try {
			StringBuilder sb = new StringBuilder();
			
			boolean firstLoop = true;
			for (MessageImpl message : messages) {
				if (encode) {
					sb.append(URLEncoder.encode(message.toString(), "UTF-8"));
				} else {
					sb.append(message.toString());
				}
				
				if (!firstLoop) {
					sb.append(",");
				}
				firstLoop = false;
			}
	
			return "[" + sb.toString() + "]";
			
		} catch (UnsupportedEncodingException uee) {
			throw new RuntimeException(DataConverter.toString(uee));
		}
	}
	
	
	
	
	public Message getAssociated() {
		return null;
	}
	


	public boolean isEmpty() {
		return false;
	}

	
	@SuppressWarnings("unchecked")
	public Set<String> keySet() {
		return jsonObject.keySet();
	}
	


	public boolean containsKey(Object key) {
		return jsonObject.containsKey(key);
	}
	


	public Object put(String key, Object value) {
		return jsonObject.put(key, value);
	}
	
	
	public Object remove(Object key) {
		return jsonObject.remove(key);
	}
	


	public void putAll(Map<? extends String, ? extends Object> m) {
		jsonObject.putAll(m);
	}
	
	
	@SuppressWarnings("unchecked")
	public Set<Entry<String, Object>> entrySet() {
		return jsonObject.entrySet();
	}
	


	public boolean containsValue(Object value) {
		return jsonObject.containsValue(value);
	}
	

	public void clear() {
		jsonObject.clear();
	}
	

	
	public int size() {
		return jsonObject.size();
	}
	
	@SuppressWarnings("unchecked")
	public Collection<Object> values() {
		return jsonObject.values();
	}
	


	public Object get(Object key) {
		return jsonObject.get(key);
	}
	
	
	private String getString(Object key) {
		try {
			return jsonObject.getString(key.toString());
		} catch (JSONException je) {
			return null;
		}
	}
	
	
	private Boolean getBoolean(Object key) {
		try {
			return jsonObject.getBoolean(key.toString());
		} catch (JSONException je) {
			return null;
		}
	}
	
	boolean isMetaMessage() {
		return getChannel().startsWith(Bayeux.META);
	}
	
	
	public String getChannel() {
		if (channel == null) {
			channel = getString(Bayeux.CHANNEL_FIELD);
		}
		
		return channel;
	}
	
	String getVersion() {
		return getString("version");
	}
	

	String getSubscription() {
		return getString(Bayeux.SUBSCRIPTION_FIELD);
	}

	void setSubscription(String subscription) {
		put(Bayeux.SUBSCRIPTION_FIELD, subscription);
	}
	
	
	String getMinimumVersion() {
		return getString("minimumVersion");
	}
	
	String getConnectionType() {
		return getString("connectionType");
	}
	
	void setChannel(String channel) {
		this.channel = channel; 
		put(Bayeux.CHANNEL_FIELD, channel);
	}

	void setVersion(String version) {
		put("version", version);
	}

	void setMinimumVersion(String minimumVersion) {
		put("minimumVersion", minimumVersion);
	}

	void setClientId(String clientId) {
		put(Bayeux.CLIENT_FIELD, clientId);
	}

	Map<String, Object> getExt() {
		if (ext == null) {
			ext = new HashMap<String, Object>();
			JSONObject obj = jsonObject.getJSONObject(Bayeux.EXT_FIELD);
			for (Object key : obj.keySet()) {
				ext.put((String) key, obj.get(key)); 
			}
		}
		
		return ext;
	}
	

	boolean isSuccessful() {
		return getBoolean(Bayeux.SUCCESSFUL_FIELD);
	}


	void setSuccessful(boolean successful) {
		put(Bayeux.SUCCESSFUL_FIELD, successful);
	}


	boolean isAuthSuccessful() {
		return getBoolean("authSuccessful");
	}


	void setAuthSuccessful(boolean authSuccessful) {
		put("authSuccessful", authSuccessful);
	}

	String getError() {
		return getString(Bayeux.ERROR_FIELD);
	}

	void setError(String error) {
		put(Bayeux.ERROR_FIELD, error);
	}

	
	String[] getSupportedConnectionTypes() {
		if (jsonObject.has("supportedConnectionTypes")) {
			JSONArray array = jsonObject.getJSONArray("supportedConnectionTypes");
			return (String[]) array.toArray(new String[array.size()]);
		} else {
			return new String[0];
		}
	}
	
	void setSupportedConnectionTypes(String... supportedConnectionTypes) {
		put("supportedConnectionTypes", JSONArray.fromObject(supportedConnectionTypes));
	}
	
	
	public String getClientId() {
		return getString(Bayeux.CLIENT_FIELD);
	}
	
	public String getId() {
		return getString(Bayeux.ID_FIELD);
	}
	
	void setId(String id) {
		put(Bayeux.ID_FIELD, id);
	}


	public String getData() {
		return getString(Bayeux.DATA_FIELD);
	}
	
	public void setData(String data) {
		put(Bayeux.DATA_FIELD, data);
	}
	
	@Override
	public String toString() {
		return jsonObject.toString();		
	}
}

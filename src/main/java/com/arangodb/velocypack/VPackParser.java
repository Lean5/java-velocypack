/*
 * DISCLAIMER
 *
 * Copyright 2016 ArangoDB GmbH, Cologne, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */

package com.arangodb.velocypack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONValue;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.arangodb.velocypack.exception.VPackBuilderException;
import com.arangodb.velocypack.exception.VPackException;
import com.arangodb.velocypack.internal.util.DateUtil;

/**
 * @author Mark - mark at arangodb.com
 *
 */
public class VPackParser {

	private static final char OBJECT_OPEN = '{';
	private static final char OBJECT_CLOSE = '}';
	private static final char ARRAY_OPEN = '[';
	private static final char ARRAY_CLOSE = ']';
	private static final char FIELD = ':';
	private static final char SEPARATOR = ',';
	private static final String NULL = "null";
	private static final String NON_REPRESENTABLE_TYPE = "(non-representable type)";
	private final Map<ValueType, VPackJsonDeserializer> deserializers;
	private final Map<String, Map<ValueType, VPackJsonDeserializer>> deserializersByName;
	private final Map<Class<?>, VPackJsonSerializer<?>> serializers;
	private final Map<String, Map<Class<?>, VPackJsonSerializer<?>>> serializersByName;

	public VPackParser() {
		super();
		deserializers = new HashMap<ValueType, VPackJsonDeserializer>();
		deserializersByName = new HashMap<String, Map<ValueType, VPackJsonDeserializer>>();
		serializers = new HashMap<Class<?>, VPackJsonSerializer<?>>();
		serializersByName = new HashMap<String, Map<Class<?>, VPackJsonSerializer<?>>>();
	}

	public String toJson(final VPackSlice vpack) throws VPackException {
		return toJson(vpack, false);
	}

	public String toJson(final VPackSlice vpack, final boolean includeNullValues) throws VPackException {
		final StringBuilder json = new StringBuilder();
		parse(null, null, vpack, json, includeNullValues);
		return json.toString();
	}

	public VPackParser registerDeserializer(
		final String attribute,
		final ValueType type,
		final VPackJsonDeserializer deserializer) {
		Map<ValueType, VPackJsonDeserializer> byName = deserializersByName.get(attribute);
		if (byName == null) {
			byName = new HashMap<ValueType, VPackJsonDeserializer>();
			deserializersByName.put(attribute, byName);
		}
		byName.put(type, deserializer);
		return this;
	}

	public VPackParser registerDeserializer(final ValueType type, final VPackJsonDeserializer deserializer) {
		deserializers.put(type, deserializer);
		return this;
	}

	private VPackJsonDeserializer getDeserializer(final String attribute, final ValueType type) {
		VPackJsonDeserializer deserializer = null;
		final Map<ValueType, VPackJsonDeserializer> byName = deserializersByName.get(attribute);
		if (byName != null) {
			deserializer = byName.get(type);
		}
		if (deserializer == null) {
			deserializer = deserializers.get(type);
		}
		return deserializer;
	}

	public <T> VPackParser registerSerializer(
		final String attribute,
		final Class<T> type,
		final VPackJsonSerializer<T> serializer) {
		Map<Class<?>, VPackJsonSerializer<?>> byName = serializersByName.get(attribute);
		if (byName == null) {
			byName = new HashMap<Class<?>, VPackJsonSerializer<?>>();
			serializersByName.put(attribute, byName);
		}
		byName.put(type, serializer);
		return this;
	}

	public <T> VPackParser registerSerializer(final Class<T> type, final VPackJsonSerializer<T> serializer) {
		serializers.put(type, serializer);
		return this;
	}

	private VPackJsonSerializer<?> getSerializer(final String attribute, final Class<?> type) {
		VPackJsonSerializer<?> serializer = null;
		final Map<Class<?>, VPackJsonSerializer<?>> byName = serializersByName.get(attribute);
		if (byName != null) {
			serializer = byName.get(type);
		}
		if (serializer == null) {
			serializer = serializers.get(type);
		}
		return serializer;
	}

	private void parse(
		final VPackSlice parent,
		final String attribute,
		final VPackSlice value,
		final StringBuilder json,
		final boolean includeNullValues) throws VPackException {

		VPackJsonDeserializer deserializer = null;
		if (attribute != null) {
			appendField(attribute, json);
			deserializer = getDeserializer(attribute, value.getType());
		}
		if (deserializer != null) {
			deserializer.deserialize(parent, attribute, value, json);
		} else {
			if (value.isObject()) {
				parseObject(value, json, includeNullValues);
			} else if (value.isArray()) {
				parseArray(value, json, includeNullValues);
			} else if (value.isBoolean()) {
				json.append(value.getAsBoolean());
			} else if (value.isString()) {
				json.append(JSONValue.toJSONString(value.getAsString()));
			} else if (value.isNumber()) {
				json.append(value.getAsNumber());
			} else if (value.isDate()) {
				json.append(JSONValue.toJSONString(DateUtil.format(value.getAsDate())));
			} else if (value.isNull()) {
				json.append(NULL);
			} else {
				json.append(JSONValue.toJSONString(NON_REPRESENTABLE_TYPE));
			}
		}
	}

	private static void appendField(final String attribute, final StringBuilder json) {
		json.append(JSONValue.toJSONString(attribute));
		json.append(FIELD);
	}

	private void parseObject(final VPackSlice value, final StringBuilder json, final boolean includeNullValues)
			throws VPackException {
		json.append(OBJECT_OPEN);
		int added = 0;
		for (final Iterator<Entry<String, VPackSlice>> iterator = value.objectIterator(); iterator.hasNext();) {
			final Entry<String, VPackSlice> next = iterator.next();
			final VPackSlice nextValue = next.getValue();
			if (!nextValue.isNull() || includeNullValues) {
				if (added++ > 0) {
					json.append(SEPARATOR);
				}
				parse(value, next.getKey(), nextValue, json, includeNullValues);
			}
		}
		json.append(OBJECT_CLOSE);
	}

	private void parseArray(final VPackSlice value, final StringBuilder json, final boolean includeNullValues)
			throws VPackException {
		json.append(ARRAY_OPEN);
		int added = 0;
		for (int i = 0; i < value.getLength(); i++) {
			final VPackSlice valueAt = value.get(i);
			if (!valueAt.isNull() || includeNullValues) {
				if (added++ > 0) {
					json.append(SEPARATOR);
				}
				parse(value, null, valueAt, json, includeNullValues);
			}
		}
		json.append(ARRAY_CLOSE);
	}

	public VPackSlice fromJson(final String json) throws VPackException {
		return fromJson(json, false);
	}

	public VPackSlice fromJson(final String json, final boolean includeNullValues) throws VPackException {
		final VPackBuilder builder = new VPackBuilder();
		final JSONParser parser = new JSONParser();
		final ContentHandler contentHandler = new VPackContentHandler(builder, includeNullValues, this);
		try {
			parser.parse(json, contentHandler);
		} catch (final ParseException e) {
			throw new VPackBuilderException(e);
		}
		return builder.slice();
	}

	public VPackSlice fromJson(final Iterable<String> jsons) throws VPackException {
		return fromJson(jsons, false);
	}

	public VPackSlice fromJson(final Iterable<String> jsons, final boolean includeNullValues) throws VPackException {
		final VPackBuilder builder = new VPackBuilder();
		final JSONParser parser = new JSONParser();
		final ContentHandler contentHandler = new VPackContentHandler(builder, includeNullValues, this);
		try {
			builder.add(ValueType.ARRAY);
			for (final String json : jsons) {
				parser.parse(json, contentHandler);
			}
			builder.close();
		} catch (final ParseException e) {
			throw new VPackBuilderException(e);
		}
		return builder.slice();
	}

	private static class VPackContentHandler implements ContentHandler {

		private final VPackBuilder builder;
		private String attribute;
		private final boolean includeNullValues;
		private final VPackParser parser;

		public VPackContentHandler(final VPackBuilder builder, final boolean includeNullValues,
			final VPackParser parser) {
			this.builder = builder;
			this.includeNullValues = includeNullValues;
			this.parser = parser;
			attribute = null;
		}

		private void add(final ValueType value) throws ParseException {
			try {
				builder.add(attribute, value);
				attribute = null;
			} catch (final VPackBuilderException e) {
				throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
			}
		}

		private void add(final String value) throws ParseException {
			try {
				builder.add(attribute, value);
				attribute = null;
			} catch (final VPackBuilderException e) {
				throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
			}
		}

		private void add(final Boolean value) throws ParseException {
			try {
				builder.add(attribute, value);
				attribute = null;
			} catch (final VPackBuilderException e) {
				throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
			}
		}

		private void add(final Double value) throws ParseException {
			try {
				builder.add(attribute, value);
				attribute = null;
			} catch (final VPackBuilderException e) {
				throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
			}
		}

		private void add(final Long value) throws ParseException {
			try {
				builder.add(attribute, value);
				attribute = null;
			} catch (final VPackBuilderException e) {
				throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
			}
		}

		private void close() throws ParseException {
			try {
				builder.close();
			} catch (final VPackBuilderException e) {
				throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
			}
		}

		@Override
		public void startJSON() throws ParseException, IOException {
		}

		@Override
		public void endJSON() throws ParseException, IOException {
		}

		@Override
		public boolean startObject() throws ParseException, IOException {
			add(ValueType.OBJECT);
			return true;
		}

		@Override
		public boolean endObject() throws ParseException, IOException {
			close();
			return true;
		}

		@Override
		public boolean startObjectEntry(final String key) throws ParseException, IOException {
			attribute = key;
			return true;
		}

		@Override
		public boolean endObjectEntry() throws ParseException, IOException {
			return true;
		}

		@Override
		public boolean startArray() throws ParseException, IOException {
			add(ValueType.ARRAY);
			return true;
		}

		@Override
		public boolean endArray() throws ParseException, IOException {
			close();
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean primitive(final Object value) throws ParseException, IOException {
			if (value == null) {
				if (includeNullValues) {
					add(ValueType.NULL);
				}
			} else {
				final VPackJsonSerializer<?> serializer = parser.getSerializer(attribute, value.getClass());
				if (serializer != null) {
					try {
						((VPackJsonSerializer<Object>) serializer).serialize(builder, attribute, value);
						attribute = null;
					} catch (final VPackBuilderException e) {
						throw new ParseException(ParseException.ERROR_UNEXPECTED_EXCEPTION);
					}
				} else if (String.class.isAssignableFrom(value.getClass())) {
					add(String.class.cast(value));
				} else if (Boolean.class.isAssignableFrom(value.getClass())) {
					add(Boolean.class.cast(value));
				} else if (Double.class.isAssignableFrom(value.getClass())) {
					add(Double.class.cast(value));
				} else if (Number.class.isAssignableFrom(value.getClass())) {
					add(Long.class.cast(value));
				}
			}
			return true;
		}

	}

}
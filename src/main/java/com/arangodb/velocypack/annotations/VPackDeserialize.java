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

package com.arangodb.velocypack.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Michele Rastelli
 */
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface VPackDeserialize {

	String UNDEFINED_BUILD_METHOD_NAME = "__UNDEFINED_BUILD_METHOD_NAME__";
	String UNDEFINED_WITH_PREFIX = "__UNDEFINED_WITH_PREFIX__";

	Class<?> builder();

	VPackPOJOBuilder builderConfig() default @VPackPOJOBuilder(buildMethodName = UNDEFINED_BUILD_METHOD_NAME,
															   withSetterPrefix = UNDEFINED_WITH_PREFIX);

}

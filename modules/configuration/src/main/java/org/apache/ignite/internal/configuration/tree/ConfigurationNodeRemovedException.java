/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.configuration.tree;

/**
 * This exception is thrown when an operation attempts to modify a node with a key that was removed.
 */
public class ConfigurationNodeRemovedException extends ConfigurationNodeModificationException {

    private static final long serialVersionUID = 2493615156271124701L;

    /**
     * The constructor.
     *
     * @param key   the key.
     */
    public ConfigurationNodeRemovedException(String key) {
        super(String.format("Named List element with key \"%s\" has been removed", key));
    }
}

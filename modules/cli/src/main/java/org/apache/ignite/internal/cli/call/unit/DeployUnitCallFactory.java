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

package org.apache.ignite.internal.cli.call.unit;

import jakarta.inject.Singleton;
import org.apache.ignite.internal.cli.core.ApiClientFactory;
import org.apache.ignite.internal.cli.core.call.ProgressTracker;
import org.apache.ignite.internal.cli.core.repl.registry.UnitsRegistry;

/** Factory for {@link DeployUnitCall}. */
@Singleton
public class DeployUnitCallFactory {

    private final ApiClientFactory factory;

    private final UnitsRegistry registry;

    public DeployUnitCallFactory(ApiClientFactory factory, UnitsRegistry registry) {
        this.factory = factory;
        this.registry = registry;
    }

    public DeployUnitCall create(ProgressTracker tracker) {
        return new DeployUnitCall(tracker, factory, registry);
    }
}

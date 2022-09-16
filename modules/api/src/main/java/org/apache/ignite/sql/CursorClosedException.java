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

package org.apache.ignite.sql;

import static org.apache.ignite.lang.ErrorGroups.Sql.CURSOR_CLOSED_ERR;

import java.util.UUID;

/**
 * Exception is thrown when query cursor data fetch attempt is performed on a closed cursor.
 */
public class CursorClosedException extends SqlException {
    /**
     * Creates a new exception instance.
     *
     */
    public CursorClosedException() {
        super(CURSOR_CLOSED_ERR);
    }

    /**
     * Creates a new exception with the given trace id, error code, detail message and cause.
     *
     * @param traceId Unique identifier of this exception.
     * @param code Full error code.
     * @param message Detail message.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public CursorClosedException(UUID traceId, int code, String message, Throwable cause) {
        super(traceId, code, message, cause);
    }
}

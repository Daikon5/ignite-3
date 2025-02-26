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

package org.apache.ignite.internal;

import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.Session;
import org.apache.ignite.tx.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * Utils to work with {@link Session}.
 */
public class SessionUtils {
    /**
     * Executes an update on a session, possibly in a transaction.
     *
     * @param sql SQL query to execute.
     * @param session Session on which to execute.
     * @param transaction Transaction in which to execute the update, or {@code null} if the update should
     *     be executed n an implicit transaction.
     */
    public static void executeUpdate(String sql, Session session, @Nullable Transaction transaction) {
        try (ResultSet ignored = session.execute(transaction, sql)) {
            // Do nothing, just adhere to the syntactic ceremony...
        }
    }

    /**
     * Executes an update on a session in an implicit transaction.
     *
     * @param sql SQL query to execute.
     * @param session Session on which to execute.
     */
    public static void executeUpdate(String sql, Session session) {
        executeUpdate(sql, session, null);
    }
}

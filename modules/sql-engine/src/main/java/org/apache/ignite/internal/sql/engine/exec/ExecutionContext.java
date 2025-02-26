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

package org.apache.ignite.internal.sql.engine.exec;

import static org.apache.ignite.lang.ErrorGroups.Common.UNEXPECTED_ERR;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.ignite.internal.logger.IgniteLogger;
import org.apache.ignite.internal.logger.Loggers;
import org.apache.ignite.internal.schema.BinaryRowConverter;
import org.apache.ignite.internal.sql.engine.exec.exp.ExpressionFactory;
import org.apache.ignite.internal.sql.engine.exec.exp.ExpressionFactoryImpl;
import org.apache.ignite.internal.sql.engine.metadata.ColocationGroup;
import org.apache.ignite.internal.sql.engine.metadata.FragmentDescription;
import org.apache.ignite.internal.sql.engine.type.IgniteTypeFactory;
import org.apache.ignite.internal.sql.engine.util.AbstractQueryContext;
import org.apache.ignite.internal.sql.engine.util.BaseQueryContext;
import org.apache.ignite.internal.sql.engine.util.TypeUtils;
import org.apache.ignite.lang.IgniteInternalException;
import org.apache.ignite.network.ClusterNode;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime context allowing access to the tables in a database.
 */
public class ExecutionContext<RowT> extends AbstractQueryContext implements DataContext {
    private static final IgniteLogger LOG = Loggers.forClass(ExecutionContext.class);

    private static final TimeZone TIME_ZONE = TimeZone.getDefault(); // TODO DistributedSqlConfiguration#timeZone

    /**
     * TODO: https://issues.apache.org/jira/browse/IGNITE-15276 Support other locales.
     */
    private static final Locale LOCALE = Locale.ENGLISH;

    private final BaseQueryContext qctx;

    private final QueryTaskExecutor executor;

    private final UUID qryId;

    private final FragmentDescription fragmentDesc;

    private final Map<String, Object> params;

    private final ClusterNode localNode;

    private final String originatingNodeName;

    private final RowHandler<RowT> handler;

    private final ExpressionFactory<RowT> expressionFactory;

    private final AtomicBoolean cancelFlag = new AtomicBoolean();

    /**
     * Need to store timestamp, since SQL standard says that functions such as CURRENT_TIMESTAMP return the same value throughout the
     * query.
     */
    private final long startTs;

    private final TxAttributes txAttributes;

    private SharedState sharedState = new SharedState();

    /**
     * Constructor.
     *
     * @param executor Task executor.
     * @param qctx Base query context.
     * @param qryId Query ID.
     * @param fragmentDesc Partitions information.
     * @param handler Row handler.
     * @param params Parameters.
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public ExecutionContext(
            BaseQueryContext qctx,
            QueryTaskExecutor executor,
            UUID qryId,
            ClusterNode localNode,
            String originatingNodeName,
            FragmentDescription fragmentDesc,
            RowHandler<RowT> handler,
            Map<String, Object> params,
            TxAttributes txAttributes
    ) {
        super(qctx);

        this.executor = executor;
        this.qctx = qctx;
        this.qryId = qryId;
        this.fragmentDesc = fragmentDesc;
        this.handler = handler;
        this.params = params;
        this.localNode = localNode;
        this.originatingNodeName = originatingNodeName;
        this.txAttributes = txAttributes;

        expressionFactory = new ExpressionFactoryImpl<>(
                this,
                this.qctx.typeFactory(),
                this.qctx.config().getParserConfig().conformance()
        );

        long ts = System.currentTimeMillis();
        startTs = ts + TIME_ZONE.getOffset(ts);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Context created [qryId={}, fragmentId={}]", qryId, fragmentId());
        }
    }

    /**
     * Get query ID.
     */
    public UUID queryId() {
        return qryId;
    }

    /**
     * Get fragment ID.
     */
    public long fragmentId() {
        return fragmentDesc.fragmentId();
    }

    /**
     * Get target mapping.
     */
    public ColocationGroup target() {
        return fragmentDesc.target();
    }

    public FragmentDescription description() {
        return fragmentDesc;
    }

    /**
     * Get remote nodes for the given exchange id.
     *
     * @param exchangeId ExchangeId to find remote nodes for.
     * @return Remote nodes for given exchangeId, or {@code null}, when remote nodes not found for given exchangeId.
     */
    @Nullable
    public List<String> remotes(long exchangeId) {
        return fragmentDesc.remotes().get(exchangeId);
    }

    /**
     * Get colocation group for the given source id.
     *
     * @param sourceId SourceId to find colocation group for.
     * @return Colocation group for given sourceId.
     */
    public ColocationGroup group(long sourceId) {
        return fragmentDesc.mapping().findGroup(sourceId);
    }

    /**
     * Get handler to access row fields.
     */
    public RowHandler<RowT> rowHandler() {
        return handler;
    }

    /**
     * Get expression factory.
     */
    public ExpressionFactory<RowT> expressionFactory() {
        return expressionFactory;
    }

    /**
     * Get originating node consistent ID.
     */
    public String originatingNodeName() {
        return originatingNodeName;
    }

    /**
     * Get local node.
     */
    public ClusterNode localNode() {
        return localNode;
    }

    /** {@inheritDoc} */
    @Override
    public SchemaPlus getRootSchema() {
        return qctx.schema();
    }

    /** {@inheritDoc} */
    @Override
    public IgniteTypeFactory getTypeFactory() {
        return qctx.typeFactory();
    }

    /** {@inheritDoc} */
    @Override
    public QueryProvider getQueryProvider() {
        return null; // TODO
    }

    /** {@inheritDoc} */
    @Override
    public Object get(String name) {
        if (Variable.CANCEL_FLAG.camelName.equals(name)) {
            return cancelFlag;
        }
        if (Variable.CURRENT_TIMESTAMP.camelName.equals(name)) {
            return startTs;
        }
        if (Variable.LOCAL_TIMESTAMP.camelName.equals(name)) {
            return startTs;
        }

        if (Variable.LOCALE.camelName.equals(name)) {
            return LOCALE;
        }

        if (name.startsWith("?")) {
            return TypeUtils.toInternal(params.get(name));
        }

        return params.get(name);
    }

    /** Gets dynamic parameters by name. */
    public Object getParameter(String name, Type storageType) {
        assert name.startsWith("?") : name;

        return TypeUtils.toInternal(params.get(name), storageType);
    }

    /**
     * Gets correlated value.
     *
     * @param id Correlation ID.
     * @return Correlated value.
     */
    public Object correlatedVariable(int id) {
        return sharedState.correlatedVariable(id);
    }

    /**
     * Sets correlated value.
     *
     * @param id Correlation ID.
     * @param value Correlated value.
     */
    public void correlatedVariable(Object value, int id) {
        sharedState.correlatedVariable(id, value);
    }

    /**
     * Updates the state in the context with the given one.
     *
     * @param state A state to update with.
     */
    public void sharedState(SharedState state) {
        sharedState = state;
    }

    /**
     * Returns the current state.
     *
     * @return Current state.
     */
    public SharedState sharedState() {
        return sharedState;
    }

    /**
     * Executes a query task.
     *
     * @param task Query task.
     */
    public void execute(RunnableX task, Consumer<Throwable> onError) {
        if (isCancelled()) {
            return;
        }

        executor.execute(qryId, fragmentId(), () -> {
            try {
                if (!isCancelled()) {
                    task.run();
                }
            } catch (Throwable e) {
                onError.accept(e);

                throw new IgniteInternalException(UNEXPECTED_ERR, "Unexpected exception", e);
            }
        });
    }

    /**
     * Submits a Runnable task for execution and returns a Future representing that task. The Future's {@code get} method will return
     * {@code null} upon <em>successful</em> completion.
     *
     * @param task the task to submit.
     * @return a {@link CompletableFuture} representing pending task
     */
    public CompletableFuture<?> submit(RunnableX task, Consumer<Throwable> onError) {
        assert !isCancelled() : "Call submit after execution was cancelled.";

        return executor.submit(qryId, fragmentId(), () -> {
            try {
                task.run();
            } catch (Throwable e) {
                onError.accept(e);

                throw new IgniteInternalException(UNEXPECTED_ERR, "Unexpected exception", e);
            }
        });
    }

    /**
     * RunnableX interface.
     */
    @FunctionalInterface
    public interface RunnableX {
        void run() throws Throwable;
    }

    /** Transaction for current context. */
    public TxAttributes txAttributes() {
        return txAttributes;
    }

    /**
     * Sets cancel flag, returns {@code true} if flag was changed by this call.
     *
     * @return {@code True} if flag was changed by this call.
     */
    public boolean cancel() {
        boolean res = !cancelFlag.get() && cancelFlag.compareAndSet(false, true);

        if (res && LOG.isTraceEnabled()) {
            LOG.trace("Context cancelled [qryId={}, fragmentId={}]", qryId, fragmentId());
        }

        return res;
    }

    public boolean isCancelled() {
        return cancelFlag.get();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExecutionContext<?> context = (ExecutionContext<?>) o;

        return qryId.equals(context.qryId) && fragmentDesc.fragmentId() == context.fragmentDesc.fragmentId();
    }

    /** Null bound. */
    public Object nullBound() {
        return BinaryRowConverter.NULL_BOUND;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(qryId, fragmentDesc.fragmentId());
    }
}

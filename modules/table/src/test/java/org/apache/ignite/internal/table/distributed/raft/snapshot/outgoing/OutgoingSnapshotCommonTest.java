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

package org.apache.ignite.internal.table.distributed.raft.snapshot.outgoing;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.apache.ignite.internal.storage.MvPartitionStorage;
import org.apache.ignite.internal.table.distributed.TableMessagesFactory;
import org.apache.ignite.internal.table.distributed.raft.snapshot.PartitionAccess;
import org.apache.ignite.internal.table.distributed.raft.snapshot.PartitionKey;
import org.apache.ignite.internal.table.distributed.raft.snapshot.message.SnapshotMetaRequest;
import org.apache.ignite.internal.table.distributed.raft.snapshot.message.SnapshotMetaResponse;
import org.apache.ignite.internal.tx.storage.state.TxStateStorage;
import org.apache.ignite.raft.jraft.conf.Configuration;
import org.apache.ignite.raft.jraft.conf.ConfigurationEntry;
import org.apache.ignite.raft.jraft.entity.LogId;
import org.apache.ignite.raft.jraft.entity.PeerId;
import org.apache.ignite.raft.jraft.storage.LogManager;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutgoingSnapshotCommonTest {
    @Mock
    private PartitionAccess partitionAccess;

    @Mock
    private MvPartitionStorage mvPartitionStorage;

    @Mock
    private TxStateStorage txStateStorage;

    @Mock
    private LogManager logManager;

    private OutgoingSnapshot snapshot;

    private final TableMessagesFactory messagesFactory = new TableMessagesFactory();

    private final PartitionKey partitionKey = new PartitionKey(UUID.randomUUID(), 1);

    @BeforeEach
    void createTestInstance() {
        when(partitionAccess.partitionKey()).thenReturn(partitionKey);

        lenient().when(partitionAccess.mvPartitionStorage()).thenReturn(mvPartitionStorage);
        lenient().when(partitionAccess.txStatePartitionStorage()).thenReturn(txStateStorage);

        snapshot = new OutgoingSnapshot(UUID.randomUUID(), partitionAccess, logManager);
    }

    @Test
    void returnsKeyFromStorage() {
        assertThat(snapshot.partitionKey(), is(partitionKey));
    }

    @Test
    void sendsSnapshotMeta() {
        when(mvPartitionStorage.lastAppliedIndex()).thenReturn(100L);
        when(txStateStorage.lastAppliedIndex()).thenReturn(100L);

        when(logManager.getTerm(100L)).thenReturn(3L);

        ConfigurationEntry configEntry = new ConfigurationEntry(new LogId(100, 3),
                configuration(List.of("peer1:3000", "peer2:3000"), List.of("learner1:3000", "learner2:3000")),
                configuration(List.of("peer1:3000"), List.of("learner1:3000"))
        );
        when(logManager.getConfiguration(100L)).thenReturn(configEntry);

        freezeSnapshot();

        SnapshotMetaResponse response = getSnapshotMetaResponse();

        assertThat(response.meta().lastIncludedIndex(), is(100L));
        assertThat(response.meta().lastIncludedTerm(), is(3L));
        assertThat(response.meta().peersList(), is(List.of("peer1:3000", "peer2:3000")));
        assertThat(response.meta().learnersList(), is(List.of("learner1:3000", "learner2:3000")));
        assertThat(response.meta().oldPeersList(), is(List.of("peer1:3000")));
        assertThat(response.meta().oldLearnersList(), is(List.of("learner1:3000")));
    }

    private static Configuration configuration(List<String> peers, List<String> learners) {
        return new Configuration(
                peers.stream().map(PeerId::parsePeer).collect(toList()),
                learners.stream().map(PeerId::parsePeer).collect(toList())
        );
    }

    private void freezeSnapshot() {
        snapshot.acquireMvLock();

        try {
            snapshot.freezeScope();
        } finally {
            snapshot.releaseMvLock();
        }
    }

    private SnapshotMetaResponse getSnapshotMetaResponse() {
        SnapshotMetaResponse response = getNullableSnapshotMetaResponse();

        assertThat(response, is(notNullValue()));

        return response;
    }

    @Nullable
    private SnapshotMetaResponse getNullableSnapshotMetaResponse() {
        SnapshotMetaRequest request = messagesFactory.snapshotMetaRequest()
                .id(snapshot.id())
                .build();

        return snapshot.handleSnapshotMetaRequest(request);
    }

    @Test
    void doesNotSendOldConfigWhenItIsNotThere() {
        @SuppressWarnings("ConstantConditions")
        ConfigurationEntry configEntry = new ConfigurationEntry(new LogId(1, 1), new Configuration(), null);
        when(logManager.getConfiguration(anyLong())).thenReturn(configEntry);

        freezeSnapshot();

        SnapshotMetaResponse response = getSnapshotMetaResponse();

        assertThat(response.meta().oldPeersList(), is(nullValue()));
        assertThat(response.meta().oldLearnersList(), is(nullValue()));
    }

    @Test
    void sendsMvIndexIfItIsAhead() {
        when(mvPartitionStorage.lastAppliedIndex()).thenReturn(100L);
        when(txStateStorage.lastAppliedIndex()).thenReturn(90L);

        when(logManager.getConfiguration(anyLong())).thenReturn(new ConfigurationEntry());

        freezeSnapshot();

        SnapshotMetaResponse response = getSnapshotMetaResponse();

        assertThat(response.meta().lastIncludedIndex(), is(100L));
    }

    @Test
    void sendsTxIndexIfItIsAhead() {
        when(mvPartitionStorage.lastAppliedIndex()).thenReturn(90L);
        when(txStateStorage.lastAppliedIndex()).thenReturn(100L);

        when(logManager.getConfiguration(anyLong())).thenReturn(new ConfigurationEntry());

        freezeSnapshot();

        SnapshotMetaResponse response = getSnapshotMetaResponse();

        assertThat(response.meta().lastIncludedIndex(), is(100L));
    }

    @Test
    void returnsNullMetaResponseWhenClosed() {
        snapshot.close();

        assertThat(getNullableSnapshotMetaResponse(), is(nullValue()));
    }
}
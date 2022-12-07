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

package org.apache.ignite.internal.raft.server.impl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.ignite.internal.raft.JraftGroupEventsListener;
import org.apache.ignite.internal.raft.Peer;
import org.apache.ignite.internal.raft.RaftGroupEventsListener;
import org.apache.ignite.raft.jraft.Status;
import org.apache.ignite.raft.jraft.entity.PeerId;
import org.apache.ignite.raft.jraft.error.RaftError;

class RaftGroupEventsListenerAdapter implements JraftGroupEventsListener {
    private final RaftGroupEventsListener delegate;

    RaftGroupEventsListenerAdapter(RaftGroupEventsListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onLeaderElected(long term) {
        delegate.onLeaderElected(term);
    }

    @Override
    public void onNewPeersConfigurationApplied(Collection<PeerId> peerIds, Collection<PeerId> learnerIds) {
        delegate.onNewPeersConfigurationApplied(peerIdsToPeers(peerIds), peerIdsToPeers(learnerIds));
    }

    @Override
    public void onReconfigurationError(Status status, Collection<PeerId> peerIds, Collection<PeerId> learnerIds, long term) {
        delegate.onReconfigurationError(convertStatus(status), peerIdsToPeers(peerIds), peerIdsToPeers(learnerIds), term);
    }

    private static List<Peer> peerIdsToPeers(Collection<PeerId> ids) {
        return ids.stream().map(id -> new Peer(id.getConsistentId())).collect(Collectors.toUnmodifiableList());
    }

    private static org.apache.ignite.internal.raft.Status convertStatus(Status status) {
        return new org.apache.ignite.internal.raft.Status(convertError(status.getRaftError()), status.getErrorMsg());
    }

    private static org.apache.ignite.internal.raft.RaftError convertError(RaftError error) {
        switch (error) {
            case SUCCESS: return org.apache.ignite.internal.raft.RaftError.SUCCESS;
            case ECATCHUP: return org.apache.ignite.internal.raft.RaftError.ECATCHUP;
            case EPERM: return org.apache.ignite.internal.raft.RaftError.EPERM;
            default: return org.apache.ignite.internal.raft.RaftError.OTHER;
        }
    }
}
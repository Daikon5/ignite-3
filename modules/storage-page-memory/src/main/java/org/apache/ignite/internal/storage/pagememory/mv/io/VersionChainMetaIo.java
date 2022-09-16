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

package org.apache.ignite.internal.storage.pagememory.mv.io;

import org.apache.ignite.internal.pagememory.io.IoVersions;
import org.apache.ignite.internal.pagememory.tree.io.BplusMetaIo;
import org.apache.ignite.internal.storage.pagememory.mv.VersionChainTree;

/**
 * IO routines for {@link VersionChainTree} meta pages.
 */
public class VersionChainMetaIo extends BplusMetaIo {
    /** Page IO type. */
    public static final short T_VERSION_CHAIN_META_IO = 9;

    /** I/O versions. */
    public static final IoVersions<VersionChainMetaIo> VERSIONS = new IoVersions<>(new VersionChainMetaIo(1));

    /**
     * Constructor.
     *
     * @param ver Page format version.
     */
    protected VersionChainMetaIo(int ver) {
        super(T_VERSION_CHAIN_META_IO, ver);
    }
}

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

package org.apache.ignite.internal.network.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.ignite.internal.logger.IgniteLogger;
import org.apache.ignite.internal.logger.Loggers;
import org.apache.ignite.internal.network.direct.DirectMarshallingUtils;
import org.apache.ignite.internal.network.direct.DirectMessageReader;
import org.apache.ignite.internal.network.message.ClassDescriptorListMessage;
import org.apache.ignite.internal.network.serialization.PerSessionSerializationService;
import org.apache.ignite.network.NetworkMessage;
import org.apache.ignite.network.serialization.MessageDeserializer;
import org.apache.ignite.network.serialization.MessageReader;

/**
 * Decodes {@link ByteBuf}s into {@link NetworkMessage}s.
 */
public class InboundDecoder extends ByteToMessageDecoder {
    /** Handler name. */
    public static final String NAME = "inbound-decoder";

    /** Logger. */
    private static final IgniteLogger LOG = Loggers.forClass(InboundDecoder.class);

    /** Message reader channel attribute key. */
    private static final AttributeKey<MessageReader> READER_KEY = AttributeKey.valueOf("READER");

    /** Message deserializer channel attribute key. */
    private static final AttributeKey<MessageDeserializer<NetworkMessage>> DESERIALIZER_KEY = AttributeKey.valueOf("DESERIALIZER");

    /** Serialization service. */
    private final PerSessionSerializationService serializationService;

    /**
     * Constructor.
     *
     * @param serializationService Serialization service.
     */
    public InboundDecoder(PerSessionSerializationService serializationService) {
        this.serializationService = serializationService;
    }

    /** {@inheritDoc} */
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        ByteBuffer buffer = in.nioBuffer();

        Attribute<MessageReader> readerAttr = ctx.channel().attr(READER_KEY);
        MessageReader reader = readerAttr.get();

        if (reader == null) {
            reader = new DirectMessageReader(serializationService.serializationRegistry(), ConnectionManager.DIRECT_PROTOCOL_VERSION);
            readerAttr.set(reader);
        }

        Attribute<MessageDeserializer<NetworkMessage>> messageAttr = ctx.channel().attr(DESERIALIZER_KEY);

        while (buffer.hasRemaining()) {
            int initialNioBufferPosition = buffer.position();

            MessageDeserializer<NetworkMessage> msg = messageAttr.get();

            try {
                // Read message type.
                if (msg == null) {
                    if (buffer.remaining() >= NetworkMessage.MSG_TYPE_SIZE_BYTES) {
                        msg = serializationService.createMessageDeserializer(DirectMarshallingUtils.getShort(buffer),
                                DirectMarshallingUtils.getShort(buffer));
                    } else {
                        break;
                    }
                }

                boolean finished = false;

                // Read message if buffer has remaining data.
                if (msg != null && buffer.hasRemaining()) {
                    reader.setCurrentReadClass(msg.klass());
                    reader.setBuffer(buffer);

                    finished = msg.readMessage(reader);
                }

                int readBytes = buffer.position() - initialNioBufferPosition;

                // Set read position to Netty's ByteBuf.
                in.readerIndex(in.readerIndex() + readBytes);

                if (finished) {
                    reader.reset();
                    messageAttr.set(null);

                    NetworkMessage message = msg.getMessage();

                    if (message instanceof ClassDescriptorListMessage) {
                        onClassDescriptorMessage((ClassDescriptorListMessage) message);
                    } else {
                        out.add(message);
                    }
                } else {
                    messageAttr.set(msg);
                }

                if (readBytes == 0) {
                    // Zero bytes were read from the buffer.
                    // This could've happened because for some types (like floats) we have to read N bytes at a time and there could be
                    // less than N bytes available.
                    break;
                }
            } catch (Throwable e) {
                LOG.debug("Failed to read message [msg={}, buf={}, reader={}, reason={}]",
                                e, msg, buffer, reader, e.getMessage()
                );

                throw e;
            }
        }
    }

    private void onClassDescriptorMessage(ClassDescriptorListMessage msg) {
        serializationService.mergeDescriptors(msg.messages());
    }
}

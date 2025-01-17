/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.teiid.infinispan.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.ProtobufTagMarshaller;
import org.infinispan.protostream.SerializationContext.MarshallerProvider;
import org.teiid.core.TeiidRuntimeException;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.metadata.Table;
import org.teiid.translator.TranslatorException;

import java.io.IOException;

public class TeiidMarshallerProvider implements MarshallerProvider {

    private Cache<String, Table> types =
            Caffeine.newBuilder().weakValues().build();
    private Cache<Table, ProtobufTagMarshaller<InfinispanDocument>> marshallers =
            Caffeine.newBuilder().weakKeys().build();

    private static ThreadLocal<InfinispanDocument> CURRENT_DOCUMENT = new ThreadLocal<>();

    @Override
    public ProtobufTagMarshaller<InfinispanDocument> getMarshaller(String typeName) {
        Table table = types.getIfPresent(typeName);
        if (table == null) {
            return null;
        }
        ProtobufTagMarshaller<InfinispanDocument> marshaller = marshallers.getIfPresent(table);
        if (marshaller == null) {
            return null;
        }
        return marshaller;
    }

    public static void setCurrentDocument(InfinispanDocument document) {
        CURRENT_DOCUMENT.set(document);
    }

    @Override
    public BaseMarshaller<?> getMarshaller(Class<?> javaClass) {
        InfinispanDocument current = CURRENT_DOCUMENT.get();
        if (current == null) {
            return null;
        }
        String type = current.getName();
        if (InfinispanDocument.class.isAssignableFrom(javaClass)) {
            return new ProtobufTagMarshaller<InfinispanDocument>() {

                @Override
                public Class<? extends InfinispanDocument> getJavaClass() {
                    return InfinispanDocument.class;
                }

                @Override
                public String getTypeName() {
                    return type;
                }

                @Override
                public InfinispanDocument read(ReadContext ctx) throws IOException {
                    throw new AssertionError();
                }

                @Override
                public void write(WriteContext ctx, InfinispanDocument infinispanDocument) throws IOException {
                    ProtobufTagMarshaller<InfinispanDocument> marshaller = getMarshaller(type);
                    if (marshaller == null) {
                        throw new IllegalStateException();
                    }
                    marshaller.write(ctx, infinispanDocument);
                }
            };
        }
        return null;
    }

    public void registerMarshaller(Table table, RuntimeMetadata metadata) {
        marshallers.get(table, (t) -> {
            try {
                types.put(ProtobufMetadataProcessor.getMessageName(table), t);
                return MarshallerBuilder.getMarshaller(table, metadata);
            } catch (TranslatorException e) {
                throw new TeiidRuntimeException(e);
            }
        });
    }

}

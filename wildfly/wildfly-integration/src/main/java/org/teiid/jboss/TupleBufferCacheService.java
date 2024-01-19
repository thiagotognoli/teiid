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
package org.teiid.jboss;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.teiid.common.buffer.BufferManager;
import org.teiid.common.buffer.TupleBuffer;
import org.teiid.common.buffer.TupleBufferCache;
import org.teiid.query.ObjectReplicator;

import java.util.function.Consumer;
import java.util.function.Supplier;

class TupleBufferCacheService implements Service {
    public Supplier<ObjectReplicator> replicatorInjector;
    protected Supplier<BufferManager> bufferMgrInjector;

    private TupleBufferCache tupleBufferCache;
    private Consumer<TupleBufferCache> tupleBufferCacheConsumer;

    public TupleBufferCacheService(Supplier<BufferManager> bufferManagerDep, Consumer<TupleBufferCache> tupleBufferSupplier) {
        this.bufferMgrInjector = bufferManagerDep;
        this.tupleBufferCacheConsumer = tupleBufferSupplier;
    }

    @Override
    public void start(StartContext context) throws StartException {
        if (this.replicatorInjector != null) {
            try {
                //use a mux name that will not conflict with any vdb
                this.tupleBufferCache = this.replicatorInjector.get().replicate("$TEIID_BM$", TupleBufferCache.class, bufferMgrInjector.get(), 0); //$NON-NLS-1$
                this.tupleBufferCacheConsumer.accept(tupleBufferCache);
            } catch (Exception e) {
                throw new StartException(e);
            }
        } else {
            this.tupleBufferCacheConsumer.accept(bufferMgrInjector.get());
        }
    }

    @Override
    public void stop(StopContext context) {
        if (this.replicatorInjector != null && this.tupleBufferCache != null) {
            this.replicatorInjector.get().stop(this.tupleBufferCache);
        }
    }
}

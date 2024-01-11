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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.teiid.common.buffer.BufferManager;
import org.teiid.services.BufferServiceImpl;

import java.util.function.Consumer;
import java.util.function.Supplier;

class BufferManagerService extends BufferServiceImpl implements Service<BufferManager> {
    private static final long serialVersionUID = -6797455072198476318L;

    public final Supplier<String> pathInjector;
    private Consumer<BufferManager> providedInstance;

    public BufferManagerService(Supplier<String> pathSupplier, Consumer<BufferManager> provides) {
        this.pathInjector = pathSupplier;
        this.providedInstance = provides;
    }

    @Override
    public void start(StartContext context) throws StartException {
        setDiskDirectory(pathInjector.get());
        start();
        providedInstance.accept(getBufferManager());
    }

    @Override
    public void stop(StopContext context) {
        stop();
    }

    @Override
    public BufferManager getValue() throws IllegalStateException,IllegalArgumentException {
        return getBufferManager();
    }

}

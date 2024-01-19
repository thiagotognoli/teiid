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

import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.modules.Module;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.teiid.cache.CacheFactory;
import org.teiid.cache.infinispan.InfinispanCacheFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

class CacheFactoryService implements Service {
    protected Supplier<EmbeddedCacheManager> cacheContainerInjector;
    private Consumer<CacheFactory> cacheFactory;

    public CacheFactoryService(Supplier<EmbeddedCacheManager> cmDep, Consumer<CacheFactory> cfCons) {
        this.cacheContainerInjector = cmDep;
        this.cacheFactory = cfCons;
    }

    @Override
    public void start(StartContext context) throws StartException {
        EmbeddedCacheManager cc = cacheContainerInjector.get();
        if (cc != null) {
            CacheFactory cacheFactory = new InfinispanCacheFactory(cc, Module.getCallerModule().getClassLoader());
            this.cacheFactory.accept(cacheFactory);
        }
        else {
            throw new StartException(IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50093));
        }
    }

    @Override
    public void stop(StopContext context) {
        this.cacheFactory = null;
    }
}

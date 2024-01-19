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
import org.jgroups.JChannel;
import org.teiid.replication.jgroups.JGroupsObjectReplicator;
import org.wildfly.clustering.jgroups.ChannelFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

class JGroupsObjectReplicatorService implements Service<JGroupsObjectReplicator> {

    public final Supplier<ChannelFactory> channelFactory;
    final Supplier<Executor> executor;
    private JGroupsObjectReplicator replicator;
    private Consumer<JGroupsObjectReplicator> replicatorConsumer;

    public JGroupsObjectReplicatorService(Supplier<ChannelFactory> channelFactorySupplier, Supplier<Executor> poolServiceSupplier, Consumer<JGroupsObjectReplicator> replicatorInstance) {
        this.channelFactory = channelFactorySupplier;
        this.executor = poolServiceSupplier;
        this.replicatorConsumer = replicatorInstance;
    }


    @Override
    public void start(StartContext context) throws StartException {
        JGroupsObjectReplicator replicator = new JGroupsObjectReplicator(new org.teiid.replication.jgroups.ChannelFactory() {
            @Override
            public JChannel createChannel(String id) throws Exception {
                JChannel c = channelFactory.get().createChannel(id);
                return c;
            }
        }, executor.get());
        this.replicatorConsumer.accept(replicator);
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public JGroupsObjectReplicator getValue() throws IllegalStateException,IllegalArgumentException {
        return replicator;
    }

}

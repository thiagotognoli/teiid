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
import org.jboss.msc.value.InjectedValue;
import org.teiid.adminapi.impl.VDBTranslatorMetaData;
import org.teiid.deployers.VDBStatusChecker;
import org.teiid.dqp.internal.datamgr.TranslatorRepository;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;

import java.util.function.Supplier;

class TranslatorService implements Service<VDBTranslatorMetaData> {
    private VDBTranslatorMetaData translator;

    final Supplier<TranslatorRepository> repositoryInjector;
    final Supplier<VDBStatusChecker> statusCheckerInjector;

    public TranslatorService(VDBTranslatorMetaData translator, Supplier<TranslatorRepository> translatorRepo, Supplier<VDBStatusChecker> statusChecker) {
        this.translator = translator;
        this.repositoryInjector = translatorRepo;
        this.statusCheckerInjector = statusChecker;
    }

    @Override
    public void start(StartContext context) throws StartException {
        this.repositoryInjector.get().addTranslatorMetadata(this.translator.getName(), this.translator);
        this.statusCheckerInjector.get().translatorAdded(this.translator.getName());
    }

    @Override
    public void stop(StopContext context) {
        this.repositoryInjector.get().removeTranslatorMetadata(this.translator.getName());
        this.statusCheckerInjector.get().translatorRemoved(this.translator.getName());
        LogManager.logInfo(LogConstants.CTX_RUNTIME, IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50010, this.translator.getName()));
    }

    @Override
    public VDBTranslatorMetaData getValue() throws IllegalStateException, IllegalArgumentException {
        return this.translator;
    }
}

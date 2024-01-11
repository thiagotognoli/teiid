/*
 * Copyright 2017-2018 The OpenTracing Authors
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.teiid.query.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.teiid.jdbc.tracing.GlobalTracerInjector;
import org.teiid.json.simple.JSONParser;
import org.teiid.json.simple.ParseException;
import org.teiid.json.simple.SimpleContentHandler;
import org.teiid.logging.CommandLogMessage;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;

import java.util.Map;

public class TeiidTracingUtil {

    private Tracer tracer;

    private static TeiidTracingUtil INSTANCE = new TeiidTracingUtil();

    public static TeiidTracingUtil getInstance() {
        return INSTANCE;
    }

    /**
     * For use by tests - GlobalTracer is not directly test friendly as the registration can only happen once.
     */
    void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Build a {@link Span} from the {@link CommandLogMessage} and incoming span context
     * @param msg
     * @param spanContextJson
     * @return
     */
    public Span buildSpan(Options options, CommandLogMessage msg, String spanContextJson) {
        if (!isTracingEnabled(options, spanContextJson)) {
            return null;
        }

        SpanBuilder spanBuilder = getTracer().spanBuilder("USER COMMAND").setSpanKind(SpanKind.SERVER);

        if (spanContextJson != null) {
            Context parent = extractSpanContext(spanContextJson);
            if (parent != null) {
                spanBuilder.setParent(parent);
            } else if (options.isTracingWithActiveSpanOnly()) {
                return null;
            }
        }

        Span span = spanBuilder.startSpan();
        span.setAttribute("component", "java-teiid"); //$NON-NLS-1$
        span.setAttribute(SemanticAttributes.DB_STATEMENT, msg.getSql());
        span.setAttribute("db.type", "teiid"); //$NON-NLS-1$
        span.setAttribute("db.instance", msg.getVdbName());
        span.setAttribute(SemanticAttributes.DB_USER, msg.getPrincipal());
        span.setAttribute("teiid-session", msg.getSessionID()); //$NON-NLS-1$
        span.setAttribute("teiid-request", msg.getRequestID()); //$NON-NLS-1$

        return span;
    }

    /**
     * Return true if tracing is enabled.
     *
     * Both arguments may be null, in which case true will be returned only if there is an active span
     * @param options
     * @param spanContextJson
     * @return
     */
    public boolean isTracingEnabled(Options options, String spanContextJson) {
        boolean withActiveSpanOnly = options == null?true:options.isTracingWithActiveSpanOnly();
        return !withActiveSpanOnly || Span.current() != Span.getInvalid() || spanContextJson != null;
    }

    /**
     * Build a {@link Span} from the {@link CommandLogMessage} and translator type
     * @param msg
     * @param translatorType
     * @return
     */
    public Span buildSourceSpan(CommandLogMessage msg, String translatorType) {
        Tracer tr = getTracer();
        if (Span.current() == null) {
            return null;
        }

        SpanBuilder spanBuilder = tr.spanBuilder("SRC COMMAND").setSpanKind(SpanKind.CLIENT); //$NON-NLS-1$

        Span span = spanBuilder.startSpan();
        span.setAttribute("component", "java-teiid-connector"); //$NON-NLS-1$
        span.setAttribute(SemanticAttributes.DB_STATEMENT, msg.getSql());
        span.setAttribute("db.type", translatorType);
        span.setAttribute(SemanticAttributes.DB_USER, msg.getPrincipal());
        span.setAttribute("teiid-source-request", msg.getSourceCommandID()); //$NON-NLS-1$

        return span;
    }

    private Tracer getTracer() {
        if (tracer != null) {
            return tracer;
        }
        return GlobalTracerInjector.getTracer();
    }

    protected Context extractSpanContext(String spanContextJson) {
        try {
            JSONParser parser = new JSONParser();
            SimpleContentHandler sch = new SimpleContentHandler();
            parser.parse(spanContextJson, sch);
            Map<String, String> result = (Map<String, String>) sch.getResult();
            Context extracted = Context.current();
            for (Map.Entry<String, String> entry : result.entrySet()) {
                extracted = extracted.with(ContextKey.named(entry.getKey()), entry.getValue());
            }
            return extracted;
        } catch (IllegalArgumentException | ClassCastException | ParseException e) {
            LogManager.logDetail(LogConstants.CTX_DQP, e, "Could not extract the span context"); //$NON-NLS-1$
            return null;
        }
    }

}
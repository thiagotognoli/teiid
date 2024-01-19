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

package org.teiid.jdbc.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("nls")
public class TestTracing {

    @Test public void testSpanContextInjection() {
        Tracer tracer = GlobalOpenTelemetry.getTracer("test");
        assertNull(GlobalTracerInjector.getSpanContext(tracer));
        Span ignored = tracer.spanBuilder("x").startSpan();
        try {
            assertEquals("{\"spanid\":\"2\",\"traceid\":\"1\"}", GlobalTracerInjector.getSpanContext(tracer));
        } finally {
            ignored.end();
        }
    }

}

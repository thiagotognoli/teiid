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
package org.teiid.jboss.rest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.jboss.resteasy.spi.InternalServerErrorException;

@Provider
public class TeiidRSExceptionHandler implements ExceptionMapper<Exception> {

    @Context
    protected HttpHeaders httpHeaders;

    @Override
    public Response toResponse(Exception e) {

        ResponseError error = new ResponseError();

        String code = "ERROR"; //$NON-NLS-1$
        if(e instanceof NotAuthorizedException){
            code = "401"; //$NON-NLS-1$
        } else if(e instanceof NotFoundException){
            code = "404"; //$NON-NLS-1$
        } else if(e instanceof InternalServerErrorException) {
            code = "500"; //$NON-NLS-1$
        } else if(e instanceof WebApplicationException) {
            code = "500"; //$NON-NLS-1$
        }
        error.setCode(code);

        error.setMessage(e.getMessage());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        error.setDetails(sw.toString());

        String type = MediaType.APPLICATION_XML;
        List<MediaType> acceptTypes = httpHeaders.getAcceptableMediaTypes();
        if(acceptTypes != null){
            for (MediaType acceptType : acceptTypes){
                if (isApplicationJsonWithParametersIgnored(acceptType)) {
                    type = MediaType.APPLICATION_JSON;
                    break;
                }
            }
        }

        return Response.serverError().entity(error).type(type).build();
    }

    private boolean isApplicationJsonWithParametersIgnored(MediaType acceptType) {
        return acceptType.getType().equals(MediaType.APPLICATION_JSON_TYPE.getType()) &&
            acceptType.getSubtype().equals(MediaType.APPLICATION_JSON_TYPE.getSubtype());
     }

    @XmlRootElement(name = "error") //$NON-NLS-1$
    @XmlType(propOrder = { "code", "message", "details"}) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    public static class ResponseError {

        private String code;

        private String message;

        private String details;

        @XmlElement(name = "code") //$NON-NLS-1$
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @XmlElement(name = "message") //$NON-NLS-1$
        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @XmlElement(name = "details") //$NON-NLS-1$
        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

    }

}

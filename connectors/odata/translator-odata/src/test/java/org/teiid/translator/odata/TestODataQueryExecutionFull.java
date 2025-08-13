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
package org.teiid.translator.odata;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.activation.DataSource;
import jakarta.xml.ws.Dispatch;
import jakarta.xml.ws.Service.Mode;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.http.HTTPBinding;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.odata4j.core.OError;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.format.FormatParser;
import org.odata4j.format.xml.EdmxFormatParser;
import org.odata4j.stax2.util.StaxUtil;
import org.teiid.adminapi.Model.Type;
import org.teiid.adminapi.impl.ModelMetaData;
import org.teiid.cdk.api.TranslationUtility;
import org.teiid.core.util.ObjectConverterUtil;
import org.teiid.core.util.PropertiesUtils;
import org.teiid.core.util.UnitTestUtil;
import org.teiid.language.Command;
import org.teiid.language.QueryExpression;
import org.teiid.metadata.Column;
import org.teiid.metadata.MetadataFactory;
import org.teiid.metadata.Procedure;
import org.teiid.query.metadata.DDLStringVisitor;
import org.teiid.query.metadata.SystemMetadata;
import org.teiid.query.metadata.TransformationMetadata;
import org.teiid.query.unittest.RealMetadataFactory;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ResultSetExecution;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.ws.WSConnection;

@SuppressWarnings({"nls", "unused"})
public class TestODataQueryExecutionFull {

    private ResultSetExecution helpExecute(String query,
            final String resultXML, String expectedURL) throws Exception {
        TransformationMetadata metadata = TestDataEntitySchemaBuilder.getNorthwindMetadataFromODataXML();
        return helpExecute(query, resultXML, expectedURL, 200, metadata);
    }

    private ResultSetExecution helpExecute(String query,
            final String resultXML, String expectedURL, int responseCode)
            throws Exception {
        TransformationMetadata metadata = TestDataEntitySchemaBuilder.getNorthwindMetadataFromODataXML();
        return helpExecute(query, resultXML, expectedURL, responseCode, metadata);
    }

    private ResultSetExecution helpExecute(String query,
            final String resultXML, String expectedURL, int responseCode,
            TransformationMetadata metadata) throws Exception {
        ODataExecutionFactory translator = new ODataExecutionFactory();
        translator.start();
        TranslationUtility utility = new TranslationUtility(metadata);
        Command cmd = utility.parseCommand(query);
        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        WSConnection connection = Mockito.mock(WSConnection.class);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MessageContext.HTTP_REQUEST_HEADERS, new HashMap<String, List<String>>());
        headers.put(WSConnection.STATUS_CODE, new Integer(responseCode));

        Dispatch<DataSource> dispatch = Mockito.mock(Dispatch.class);
        Mockito.when(dispatch.getRequestContext()).thenReturn(headers);
        Mockito.when(dispatch.getResponseContext()).thenReturn(headers);

        Mockito.when(connection.createDispatch(Mockito.eq(HTTPBinding.HTTP_BINDING), Mockito.anyString(), Mockito.eq(DataSource.class), Mockito.eq(Mode.MESSAGE))).thenReturn(dispatch);

        DataSource ds = new DataSource() {
            @Override
            public OutputStream getOutputStream() throws IOException {
                return new ByteArrayOutputStream();
            }
            @Override
            public String getName() {
                return "result";
            }
            @Override
            public InputStream getInputStream() throws IOException {
                ByteArrayInputStream in = new ByteArrayInputStream(resultXML.getBytes());
                return in;
            }
            @Override
            public String getContentType() {
                return "application/xml";
            }
        };
        Mockito.when(dispatch.invoke(Mockito.nullable(DataSource.class))).thenReturn(ds);

        ResultSetExecution execution = translator.createResultSetExecution((QueryExpression)cmd, context, utility.createRuntimeMetadata(), connection);
        execution.execute();

        ArgumentCaptor<String> endpoint = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> binding = ArgumentCaptor.forClass(String.class);

        Mockito.verify(connection).createDispatch(binding.capture(), endpoint.capture(), Mockito.eq(DataSource.class), Mockito.eq(Mode.MESSAGE));
        assertEquals(expectedURL, URLDecoder.decode(endpoint.getValue(), "utf-8"));
        return execution;
    }

    @Test
    public void testJoinSelectMany() throws Exception {
        String query = "SELECT * FROM Categories LEFT JOIN Products ON Categories.CategoryID = Products.CategoryID";
        String expectedURL = "Categories/Products?$filter=CategoryID eq CategoryID&$select=CategoryID,CategoryName,Description,Picture,ProductID,ProductName,SupplierID,QuantityPerUnit,UnitPrice,UnitsInStock,UnitsOnOrder,ReorderLevel,Discontinued";

        FileReader reader = new FileReader(UnitTestUtil.getTestDataFile("categories.xml"));
        ResultSetExecution excution = helpExecute(query, ObjectConverterUtil.convertToString(reader), expectedURL);
        reader.close();
    }

    @Test
    public void testJoinSelectOne() throws Exception {
        String query = "SELECT * FROM Products LEFT JOIN Categories ON Products.CategoryID = Categories.CategoryID";
        String expectedURL = "Products/Categories?$filter=CategoryID eq CategoryID&$select=ProductID,ProductName,SupplierID,CategoryID,QuantityPerUnit,UnitPrice,UnitsInStock,UnitsOnOrder,ReorderLevel,Discontinued,CategoryName,Description,Picture";

        FileReader reader = new FileReader(UnitTestUtil.getTestDataFile("categories.xml"));
        ResultSetExecution excution = helpExecute(query, ObjectConverterUtil.convertToString(reader), expectedURL);
        reader.close();
    }

    @Test
    public void testJoinSelectMultiply() throws Exception {
        String query = 
            "SELECT * FROM Orders" +
                " LEFT JOIN Customers ON Customers.CustomerID = Orders.CustomerID" +
                " LEFT JOIN Employees ON Employees.EmployeeID = Orders.EmployeeID";
        String expectedURL = "Orders/Customers/Employees?$filter=CustomerID eq CustomerIDEmployeeID eq EmployeeID&$select=OrderID,CustomerID,EmployeeID,OrderDate,RequiredDate,ShippedDate,ShipVia,Freight,ShipName,ShipAddress,ShipCity,ShipRegion,ShipPostalCode,ShipCountry,CompanyName,ContactName,ContactTitle,Mailing,Shipping,LastName,FirstName,Title,TitleOfCourtesy,BirthDate,HireDate,Address,City,Region,PostalCode,Country,HomePhone,Extension,Photo,Notes,ReportsTo,PhotoPath";

        FileReader reader = new FileReader(UnitTestUtil.getTestDataFile("orders.xml"));
        ResultSetExecution excution = helpExecute(query, ObjectConverterUtil.convertToString(reader), expectedURL);
        reader.close();
    }

    @Test
    public void testJoinSelectMultiplyAlias() throws Exception {
        String query = 
            "SELECT c.* FROM Orders AS o" +
                " LEFT JOIN Customers AS c ON c.CustomerID = o.CustomerID" +
                " LEFT JOIN Employees AS e ON e.EmployeeID = o.EmployeeID";
        String expectedURL = "Orders/Customers/Employees?$filter=CustomerID eq CustomerIDEmployeeID eq EmployeeID&$select=CustomerID,CompanyName,ContactName,ContactTitle,Mailing,Shipping";

        FileReader reader = new FileReader(UnitTestUtil.getTestDataFile("orders.xml"));
        ResultSetExecution excution = helpExecute(query, ObjectConverterUtil.convertToString(reader), expectedURL);
        reader.close();
    }
 
    @Test
    public void testJoinSelectMultiplyOnlyFields() throws Exception {
        String query = 
            "SELECT o.* FROM Orders AS o" +
                " LEFT JOIN Customers AS c ON c.CustomerID = o.CustomerID" +
                " LEFT JOIN Employees AS e ON e.EmployeeID = o.EmployeeID";
        String expectedURL = "Orders/Customers/Employees?$filter=CustomerID eq CustomerIDEmployeeID eq EmployeeID&$select=OrderID,CustomerID,EmployeeID,OrderDate,RequiredDate,ShippedDate,ShipVia,Freight,ShipName,ShipAddress,ShipCity,ShipRegion,ShipPostalCode,ShipCountry";
        

        FileReader reader = new FileReader(UnitTestUtil.getTestDataFile("orders.xml"));
        ResultSetExecution excution = helpExecute(query, ObjectConverterUtil.convertToString(reader), expectedURL);
        reader.close();
    }

}
